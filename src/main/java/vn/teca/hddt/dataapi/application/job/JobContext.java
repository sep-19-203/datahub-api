package vn.teca.hddt.dataapi.application.job;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.domain.job.Job;

/** Ngữ cảnh một lần thử job: gia hạn lease, ghi tiến độ, phát hiện mất quyền sở hữu (rào worker_id + attempt_no). */
public final class JobContext {
    private final Job job;
    private final JobRepository jobs;
    private final String workerId;
    private final Duration lease;
    private final AtomicBoolean leaseLost = new AtomicBoolean(false);

    public JobContext(Job job, JobRepository jobs, String workerId, Duration lease) {
        this.job = job;
        this.jobs = jobs;
        this.workerId = workerId;
        this.lease = lease;
    }

    public Job job() {
        return job;
    }

    public UUID jobId() {
        return job.jobId();
    }

    public int attemptNo() {
        return job.attemptNo();
    }

    public String workerId() {
        return workerId;
    }

    /** Gia hạn lease kèm tiến độ (JSON); ném {@link LeaseLostException} nếu job đã bị worker khác nhận. */
    public void progress(String progressJson) {
        if (!jobs.renewLease(job.jobId(), workerId, job.attemptNo(), lease, progressJson)) {
            leaseLost.set(true);
        }
        ensureOwned();
    }

    public void markLeaseLost() {
        leaseLost.set(true);
    }

    public boolean leaseLost() {
        return leaseLost.get();
    }

    public void ensureOwned() {
        if (leaseLost.get() || Thread.currentThread().isInterrupted()) {
            throw new LeaseLostException(job.jobId());
        }
    }
}
