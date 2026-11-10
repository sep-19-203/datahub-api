package vn.teca.hddt.dataapi.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobHandler;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.infrastructure.worker.JobWorker;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;
import vn.teca.hddt.dataapi.support.TestProps;

class JobWorkerTest {
    final InMemoryJobRepository repo = new InMemoryJobRepository();
    JobWorker worker;
    JobWorker second;

    @AfterEach
    void stopWorkers() {
        if (worker != null) {
            worker.stop();
        }
        if (second != null) {
            second.stop();
        }
    }

    UUID enqueue(JobType type, int maxAttempts) {
        UUID id = UUID.randomUUID();
        repo.insertIfAbsent(new NewJob(id, type, type.defaultCode(), "c", "r-" + id, "h", "{}", null, maxAttempts));
        return id;
    }

    static JobHandler handler(JobType type, Function<JobContext, Completion> body) {
        return new JobHandler() {
            @Override
            public Set<JobType> types() {
                return Jdk8Compat.setOf(type);
            }

            @Override
            public Completion execute(JobContext ctx) {
                return body.apply(ctx);
            }
        };
    }

    static HddtProperties fastPolling() {
        HddtProperties p = TestProps.defaults();
        return new HddtProperties(p.api(), p.doris(), p.dse(), p.s3(), p.mstStatus(), p.hd1(), p.hd5(), p.hd7(), p.hd8(), p.tms(), p.export(),
                new HddtProperties.Worker(true, 2, Duration.ofMinutes(2), Duration.ofMillis(20), 3,
                        Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofSeconds(5), Jdk8Compat.mapOf()),
                p.security(), Jdk8Compat.mapOf());
    }

    JobWorker start(JobHandler... handlers) {
        worker = new JobWorker(repo, Jdk8Compat.listOf(handlers), fastPolling(), new SimpleMeterRegistry());
        worker.start();
        return worker;
    }

    Job await(UUID id, JobState state) throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            Job j = repo.get(id);
            if (j.state() == state) {
                return j;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("job " + id + " không đạt " + state + ": " + repo.get(id).state());
    }

    @Test
    void success_isPublishedOnlyAfterHandlerReturns() throws Exception {
        UUID id = enqueue(JobType.PURCHASE_LIST, 3);
        start(handler(JobType.PURCHASE_LIST, ctx -> new Completion("hddt", "p/", "s3://hddt/p/x_*", "{}", 5L, "Thành công")));
        Job j = await(id, JobState.SUCCEEDED);
        assertThat(j.resultUrl()).isEqualTo("s3://hddt/p/x_*");
        assertThat(j.rowCount()).isEqualTo(5);
        assertThat(j.attemptNo()).isEqualTo(1);
        assertThat(repo.events).contains("STARTED", "SUCCEEDED");
    }

    @Test
    void transientError_schedulesRetry_permanentError_failsWithSafeMessage() throws Exception {
        UUID t = enqueue(JobType.PURCHASE_LIST, 3);
        UUID p = enqueue(JobType.SALES_LIST, 3);
        start(handler(JobType.PURCHASE_LIST, ctx -> {
            throw new DataAccessResourceFailureException("mất kết nối Doris");
        }), handler(JobType.SALES_LIST, ctx -> {
            throw new PermanentJobException("HDDT-400-FILE", "File sai cấu trúc");
        }));
        Job retry = await(t, JobState.RETRY_WAIT);
        assertThat(retry.publicStatus().name()).isEqualTo("DANG_XU_LY");
        assertThat(retry.nextAttemptAt()).isAfter(java.time.Instant.now().plusSeconds(20));
        Job failed = await(p, JobState.FAILED);
        assertThat(failed.description()).isEqualTo("File sai cấu trúc");
        assertThat(failed.errorCode()).isEqualTo("HDDT-400-FILE");
    }

    @Test
    void lastAttemptTransientError_isFinalFailure() throws Exception {
        UUID id = enqueue(JobType.PURCHASE_LIST, 1);
        start(handler(JobType.PURCHASE_LIST, ctx -> {
            throw new DataAccessResourceFailureException("down");
        }));
        assertThat(await(id, JobState.FAILED).errorCode()).isEqualTo("HDDT-503");
    }

    @Test
    void resultOfWorkerThatLostItsLease_isDiscarded() throws Exception {
        UUID id = enqueue(JobType.PURCHASE_LIST, 3);
        AtomicInteger calls = new AtomicInteger();
        start(handler(JobType.PURCHASE_LIST, ctx -> {
            calls.incrementAndGet();
            // Pod khác nhận lại job (lease hết hạn) trong lúc pod này còn chạy
            Job j = repo.get(ctx.jobId());
            repo.put(new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(),
                    j.payloadJson(), j.subjectMst(), JobState.RUNNING, j.attemptNo() + 1, j.maxAttempts(), j.createdAt(),
                    j.startedAt(), null, j.nextAttemptAt(), "other-pod", repo.now.plusSeconds(600), null, null, null,
                    null, null, null, null, null, null));
            return new Completion("hddt", "p/", "s3://hddt/p/stale_*", "{}", 1L, "Thành công");
        }));
        Thread.sleep(400);
        Job j = repo.get(id);
        assertThat(j.state()).isEqualTo(JobState.RUNNING);
        assertThat(j.workerId()).isEqualTo("other-pod");
        assertThat(j.resultUrl()).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void twoWorkersNeverRunTheSameJob() throws Exception {
        for (int i = 0; i < 20; i++) {
            enqueue(JobType.PURCHASE_LIST, 3);
        }
        AtomicInteger runs = new AtomicInteger();
        JobHandler h = handler(JobType.PURCHASE_LIST, ctx -> {
            runs.incrementAndGet();
            return new Completion(null, null, null, "{}", 0L, "ok");
        });
        start(h);
        second = new JobWorker(repo, Jdk8Compat.listOf(h), fastPolling(), new SimpleMeterRegistry());
        second.start();
        for (int i = 0; i < 250 && runs.get() < 20; i++) {
            Thread.sleep(20);
        }
        Thread.sleep(150);
        assertThat(runs.get()).isEqualTo(20);
        assertThat(worker.workerId()).isNotEqualTo(second.workerId());
    }
}
