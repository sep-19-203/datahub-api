package vn.teca.hddt.dataapi.infrastructure.worker;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobFailures;
import vn.teca.hddt.dataapi.application.job.JobHandler;
import vn.teca.hddt.dataapi.application.job.LeaseLostException;
import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;

/**
 * Worker trong pod: hỏi DB theo chu kỳ, nhận job bằng SKIP LOCKED, chạy trên pool giới hạn, gia hạn lease nền
 * (lease/3). Lỗi tạm thời ⇒ RETRY_WAIT có backoff mũ; hết lượt hoặc lỗi vĩnh viễn ⇒ FAILED. Pod dừng ⇒ ngừng nhận,
 * chờ job đang chạy tối đa shutdownGrace; job chưa xong sẽ được pod khác nhận lại khi lease hết hạn.
 */
@Component
@ConditionalOnProperty(prefix = "hddt.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobs;
    private JobResultPublisher resultPublisher;
    @Autowired
    private Environment environment;
    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);
    private final HddtProperties.Worker cfg;
    private final MeterRegistry metrics;
    private final String workerId;
    private final Semaphore slots;
    private final Map<JobType, AtomicInteger> localRunning = new ConcurrentHashMap<>();
    private volatile boolean running;
    private ScheduledExecutorService scheduler;
    private ExecutorService executor;

    public JobWorker(JobRepository jobs, List<JobHandler> handlerBeans, HddtProperties props, MeterRegistry metrics) {
        this.jobs = jobs;
        this.cfg = props.worker();
        this.metrics = metrics;
        for (JobHandler h : handlerBeans) {
            for (JobType t : h.types()) {
                if (handlers.put(t, h) != null) {
                    throw new IllegalStateException("Hai handler cho " + t);
                }
            }
        }
        this.workerId = hostName() + ":" + UUID.randomUUID().toString().substring(0, 8);
        this.slots = new Semaphore(cfg.concurrency());
        for (JobType t : JobType.values()) {
            localRunning.put(t, new AtomicInteger());
        }
        metrics.gauge("hddt.worker.running", slots, s -> cfg.concurrency() - s.availablePermits());
    }

    @Autowired(required = false)
    public void setResultPublisher(JobResultPublisher resultPublisher) {
        this.resultPublisher = resultPublisher;
    }

    private void publishResult(UUID jobId) {
        if (resultPublisher == null) {
            log.warn("JOB_RESULT_SKIPPED jobId={} workerId={} reason=publisher_absent; kiểm tra hddt.job-result.enabled và bản JAR đang chạy", jobId, workerId);
            return;
        }
        try {
            log.info("JOB_RESULT_LOAD jobId={} workerId={}", jobId, workerId);
            Job finished = jobs.findById(jobId).orElseThrow(() -> new IllegalStateException("Không tìm thấy job " + jobId));
            log.info("JOB_RESULT_LOADED jobId={} clientId={} state={} rowCount={} resultUrlPresent={}",
                    jobId, finished.clientId(), finished.state(), finished.rowCount(), finished.resultUrl() != null);
            resultPublisher.publish(finished);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("JOB_RESULT_INTERRUPTED jobId={}; gửi bị ngắt, chưa xác nhận broker, không lưu hàng chờ", jobId);
        } catch (Exception e) {
            log.error("JOB_RESULT_FAILED jobId={} errorType={} causeType={} detail={}; chưa xác nhận broker, không lưu hàng chờ",
                    jobId, e.getClass().getSimpleName(), e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName(),
                    JobFailures.mask(e.toString()));
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "worker";
        }
    }

    public String workerId() {
        return workerId;
    }

    @Override
    public void start() {
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "job-scheduler");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger n = new AtomicInteger();
        executor = Executors.newFixedThreadPool(cfg.concurrency(), r -> new Thread(r, "job-exec-" + n.incrementAndGet()));
        running = true;
        scheduler.scheduleWithFixedDelay(this::pollSafely, 0, cfg.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("JobWorker {} khởi động: concurrency={}, lease={}", workerId, cfg.concurrency(), cfg.lease());
        log.info("JOB_RESULT_WORKER workerId={} publisherPresent={} enabledProperty={} activeProfiles={}", workerId,
                resultPublisher != null, environment == null ? "unknown" : environment.getProperty("hddt.job-result.enabled", "false"),
                environment == null ? "unknown" : String.join(",", environment.getActiveProfiles()));
        if (resultPublisher == null) {
            log.warn("JOB_RESULT_DISABLED workerId={}; worker không có publisher, job xong sẽ không gửi Kafka; kiểm tra hddt.job-result.enabled", workerId);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(cfg.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("JobWorker {} hết thời gian chờ; ngắt job đang chạy (lease sẽ hết hạn và được nhận lại)", workerId);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100; // dừng trước web server / datasource
    }

    void pollSafely() {
        try {
            poll();
        } catch (RuntimeException e) {
            log.warn("Lỗi khi nhận job: {}", JobFailures.mask(e.getMessage()));
        }
    }

    /** Nhận job tới khi hết slot hoặc hết job sẵn sàng. */
    void poll() {
        while (running && slots.tryAcquire()) {
            Optional<Job> claimed;
            try {
                claimed = jobs.claimNext(workerId, eligibleTypes(), cfg.lease());
            } catch (RuntimeException e) {
                slots.release();
                throw e;
            }
            if (!claimed.isPresent()) {
                slots.release();
                return;
            }
            Job job = claimed.get();
            localRunning.get(job.type()).incrementAndGet();
            executor.submit(() -> {
                try {
                    run(job);
                } catch (RuntimeException | Error e) {
                    log.error("JOB_WORKER_UNHANDLED jobId={} workerId={} errorType={} detail={}",
                            job.jobId(), workerId, e.getClass().getSimpleName(), JobFailures.mask(e.toString()));
                    throw e;
                } finally {
                    localRunning.get(job.type()).decrementAndGet();
                    slots.release();
                }
            });
        }
    }

    List<JobType> eligibleTypes() {
        List<JobType> out = new ArrayList<>();
        for (JobType t : handlers.keySet()) {
            if (jobs.countRunning(t) < cfg.maxRunning(t)) {
                out.add(t);
            }
        }
        return out;
    }

    void run(Job job) {
        MDC.put("jobId", job.jobId().toString());
        MDC.put("clientId", job.clientId());
        Timer.Sample sample = Timer.start(metrics);
        JobContext ctx = new JobContext(job, jobs, workerId, cfg.lease());
        jobs.recordEvent(job.jobId(), "STARTED", job.attemptNo(), workerId, null);
        long period = Math.max(1000, cfg.lease().toMillis() / 3);
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!jobs.renewLease(job.jobId(), workerId, job.attemptNo(), cfg.lease(), null)) {
                    ctx.markLeaseLost();
                }
            } catch (RuntimeException e) {
                log.warn("Không gia hạn được lease job {}: {}", job.jobId(), e.getMessage());
            }
        }, period, period, TimeUnit.MILLISECONDS);
        String outcome = "error";
        boolean completed = false;
        try {
            if (job.attemptNo() > job.maxAttempts()) {
                log.info("JOB_FINALIZE_BEGIN jobId={} target=FAILED reason=max_attempts attempt={}", job.jobId(), job.attemptNo());
                completed = jobs.markFailed(job.jobId(), workerId, job.attemptNo(), "HDDT-500-ATTEMPTS",
                        "Vượt số lần thử", "Job vượt số lần thử tối đa");
                log.info("JOB_FINALIZE_END jobId={} target=FAILED applied={}", job.jobId(), completed);
                outcome = "failed";
                return;
            }
            JobHandler handler = handlers.get(job.type());
            JobRepository.Completion completion = handler.execute(ctx);
            log.info("JOB_HANDLER_DONE jobId={} type={} attempt={} rowCount={} resultUrlPresent={}",
                    job.jobId(), job.type(), job.attemptNo(), completion.rowCount(), completion.resultUrl() != null);
            ctx.ensureOwned();
            log.info("JOB_FINALIZE_BEGIN jobId={} target=SUCCEEDED attempt={}", job.jobId(), job.attemptNo());
            if (jobs.markSucceeded(job.jobId(), workerId, job.attemptNo(), completion)) {
                completed = true;
                log.info("JOB_FINALIZE_END jobId={} target=SUCCEEDED applied=true", job.jobId());
                outcome = "succeeded";
                jobs.recordEvent(job.jobId(), "SUCCEEDED", job.attemptNo(), workerId, null);
            } else {
                log.warn("JOB_FINALIZE_END jobId={} target=SUCCEEDED applied=false", job.jobId());
                log.warn("Job {} đã bị worker khác nhận; bỏ kết quả lần thử {}", job.jobId(), job.attemptNo());
                outcome = "lease_lost";
            }
        } catch (LeaseLostException e) {
            log.warn("Job {} mất lease ở lần thử {}", job.jobId(), job.attemptNo());
            outcome = "lease_lost";
        } catch (Throwable t) {
            if (completed) {
                log.error("Job {} đã chốt kết quả nhưng ghi event thất bại: {}", job.jobId(), JobFailures.mask(t.toString()));
            } else {
                outcome = fail(job, t);
                completed = "failed".equals(outcome);
            }
        } finally {
            heartbeat.cancel(false);
            if (completed) {
                publishResult(job.jobId());
            } else {
                log.info("JOB_RESULT_SKIPPED jobId={} workerId={} reason=terminal_not_confirmed outcome={}",
                        job.jobId(), workerId, outcome);
            }
            sample.stop(metrics.timer("hddt.job.duration", "type", job.type().name(), "outcome", outcome));
            metrics.counter("hddt.job.outcome", "type", job.type().name(), "outcome", outcome).increment();
            MDC.remove("jobId");
            MDC.remove("clientId");
        }
    }

    private String fail(Job job, Throwable t) {
        JobFailures.Failure f = JobFailures.classify(t);
        if (f.retryable() && job.attemptNo() < job.maxAttempts()) {
            Instant next = Instant.now().plus(backoff(job.attemptNo()));
            jobs.markRetry(job.jobId(), workerId, job.attemptNo(), next, f.errorCode(), f.internalMessage());
            jobs.recordEvent(job.jobId(), "RETRY_SCHEDULED", job.attemptNo(), workerId, f.internalMessage());
            log.warn("Job {} lỗi tạm thời lần {} — thử lại lúc {}: {}", job.jobId(), job.attemptNo(), next, f.internalMessage());
            return "retry";
        }
        log.info("JOB_FINALIZE_BEGIN jobId={} target=FAILED attempt={} errorCode={}", job.jobId(), job.attemptNo(), f.errorCode());
        if (!jobs.markFailed(job.jobId(), workerId, job.attemptNo(), f.errorCode(), f.internalMessage(), f.description())) {
            log.warn("JOB_FINALIZE_END jobId={} target=FAILED applied=false", job.jobId());
            return "lease_lost";
        }
        log.info("JOB_FINALIZE_END jobId={} target=FAILED applied=true", job.jobId());
        try {
            jobs.recordEvent(job.jobId(), "FAILED", job.attemptNo(), workerId, f.internalMessage());
        } catch (RuntimeException e) {
            log.error("Job {} đã chốt LOI nhưng ghi event thất bại: {}", job.jobId(), JobFailures.mask(e.toString()));
        }
        if (f.errorCode() != null && f.errorCode().startsWith("HDDT-500")) {
            log.error("Job {} thất bại ({}): {}", job.jobId(), f.errorCode(), f.internalMessage());
        } else {
            // Từ chối nghiệp vụ / dữ liệu đầu vào (không phải lỗi hệ thống)
            log.warn("Job {} kết thúc LOI ({}): {}", job.jobId(), f.errorCode(), f.internalMessage());
        }
        return "failed";
    }

    Duration backoff(int attemptNo) {
        long base = cfg.retryBackoff().toMillis();
        long ms = base * (1L << Math.min(10, Math.max(0, attemptNo - 1)));
        return Duration.ofMillis(Math.min(ms, cfg.maxRetryBackoff().toMillis()));
    }
}
