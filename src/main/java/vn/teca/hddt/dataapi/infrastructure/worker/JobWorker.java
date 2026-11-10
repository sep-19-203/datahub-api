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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

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
        try {
            if (job.attemptNo() > job.maxAttempts()) {
                jobs.markFailed(job.jobId(), workerId, job.attemptNo(), "HDDT-500-ATTEMPTS",
                        "Vượt số lần thử", "Job vượt số lần thử tối đa");
                outcome = "failed";
                return;
            }
            JobHandler handler = handlers.get(job.type());
            JobRepository.Completion completion = handler.execute(ctx);
            ctx.ensureOwned();
            if (jobs.markSucceeded(job.jobId(), workerId, job.attemptNo(), completion)) {
                jobs.recordEvent(job.jobId(), "SUCCEEDED", job.attemptNo(), workerId, null);
                outcome = "succeeded";
            } else {
                log.warn("Job {} đã bị worker khác nhận; bỏ kết quả lần thử {}", job.jobId(), job.attemptNo());
                outcome = "lease_lost";
            }
        } catch (LeaseLostException e) {
            log.warn("Job {} mất lease ở lần thử {}", job.jobId(), job.attemptNo());
            outcome = "lease_lost";
        } catch (Throwable t) {
            outcome = fail(job, t);
        } finally {
            heartbeat.cancel(false);
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
        jobs.markFailed(job.jobId(), workerId, job.attemptNo(), f.errorCode(), f.internalMessage(), f.description());
        jobs.recordEvent(job.jobId(), "FAILED", job.attemptNo(), workerId, f.internalMessage());
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
