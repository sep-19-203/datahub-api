package vn.teca.hddt.dataapi.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;

/** JobRepository trong bộ nhớ cùng ngữ nghĩa rào (worker + attempt + RUNNING) với bản DSE — chỉ dùng cho test. */
public class InMemoryJobRepository implements JobRepository {
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    public final List<String> events = new ArrayList<>();
    public Instant now = Instant.parse("2026-10-31T03:00:00Z");

    public synchronized Job get(UUID id) {
        return jobs.get(id);
    }

    public synchronized void put(Job j) {
        jobs.put(j.jobId(), j);
    }

    @Override
    public synchronized Optional<Job> insertIfAbsent(NewJob n) {
        boolean dup = jobs.values().stream().anyMatch(j -> j.clientId().equals(n.clientId()) && j.requestId().equals(n.requestId()));
        if (dup) {
            return Optional.empty();
        }
        Job j = new Job(n.jobId(), n.type(), n.jobCode(), n.clientId(), n.requestId(), n.requestHash(), n.payloadJson(),
                n.subjectMst(), JobState.QUEUED, 0, n.maxAttempts(), now, null, null, now, null, null, null, null, null,
                null, null, null, null, null, null);
        jobs.put(j.jobId(), j);
        return Optional.of(j);
    }

    @Override
    public synchronized Optional<Job> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public synchronized Optional<Job> findByClientRequest(String clientId, String requestId) {
        return jobs.values().stream().filter(j -> j.clientId().equals(clientId) && j.requestId().equals(requestId)).findFirst();
    }

    @Override
    public synchronized long countActiveByClient(String clientId) {
        return jobs.values().stream().filter(j -> j.clientId().equals(clientId) && j.state().isActive()).count();
    }

    @Override
    public synchronized long countActiveBySubject(String subjectMst) {
        return jobs.values().stream().filter(j -> subjectMst.equals(j.subjectMst()) && j.state().isActive()).count();
    }

    @Override
    public synchronized long countRunning(JobType type) {
        return jobs.values().stream().filter(j -> j.type() == type && j.state() == JobState.RUNNING
                && j.leaseUntil() != null && j.leaseUntil().isAfter(now)).count();
    }

    @Override
    public synchronized Optional<Job> claimNext(String workerId, Collection<JobType> types, Duration lease) {
        Optional<Job> c = jobs.values().stream().filter(j -> types.contains(j.type()))
                .filter(j -> ((j.state() == JobState.QUEUED || j.state() == JobState.RETRY_WAIT) && !j.nextAttemptAt().isAfter(now))
                        || (j.state() == JobState.RUNNING && j.leaseUntil().isBefore(now)))
                .min(Comparator.comparing(Job::createdAt));
        c.ifPresent(j -> jobs.put(j.jobId(), with(j, JobState.RUNNING, j.attemptNo() + 1, workerId, now.plus(lease))));
        return c.map(j -> jobs.get(j.jobId()));
    }

    private static Job with(Job j, JobState s, int attempt, String worker, Instant lease) {
        return new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(), j.payloadJson(),
                j.subjectMst(), s, attempt, j.maxAttempts(), j.createdAt(), j.startedAt(), j.finishedAt(),
                j.nextAttemptAt(), worker, lease, j.resultBucket(), j.resultPrefix(), j.resultUrl(), j.resultManifestJson(),
                j.rowCount(), j.description(), j.errorCode(), j.errorMessage(), j.expiredAt());
    }

    private boolean owned(UUID id, String worker, int attempt) {
        Job j = jobs.get(id);
        return j != null && j.state() == JobState.RUNNING && worker.equals(j.workerId()) && j.attemptNo() == attempt;
    }

    @Override
    public synchronized boolean renewLease(UUID jobId, String workerId, int attemptNo, Duration lease, String progressJson) {
        if (!owned(jobId, workerId, attemptNo)) {
            return false;
        }
        Job j = jobs.get(jobId);
        jobs.put(jobId, with(j, JobState.RUNNING, attemptNo, workerId, now.plus(lease)));
        return true;
    }

    @Override
    public synchronized boolean markSucceeded(UUID jobId, String workerId, int attemptNo, Completion c) {
        if (!owned(jobId, workerId, attemptNo)) {
            return false;
        }
        Job j = jobs.get(jobId);
        jobs.put(jobId, new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(),
                j.payloadJson(), j.subjectMst(), JobState.SUCCEEDED, attemptNo, j.maxAttempts(), j.createdAt(),
                j.startedAt(), now, j.nextAttemptAt(), workerId, null, c.resultBucket(), c.resultPrefix(), c.resultUrl(),
                c.manifestJson(), c.rowCount(), c.description(), null, null, null));
        return true;
    }

    @Override
    public synchronized boolean markRetry(UUID jobId, String workerId, int attemptNo, Instant next, String code, String msg) {
        if (!owned(jobId, workerId, attemptNo)) {
            return false;
        }
        Job j = jobs.get(jobId);
        jobs.put(jobId, new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(),
                j.payloadJson(), j.subjectMst(), JobState.RETRY_WAIT, attemptNo, j.maxAttempts(), j.createdAt(),
                j.startedAt(), null, next, null, null, null, null, null, null, null, j.description(), code, msg, null));
        return true;
    }

    @Override
    public synchronized boolean markFailed(UUID jobId, String workerId, int attemptNo, String code, String msg, String mta) {
        if (!owned(jobId, workerId, attemptNo)) {
            return false;
        }
        Job j = jobs.get(jobId);
        jobs.put(jobId, new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(),
                j.payloadJson(), j.subjectMst(), JobState.FAILED, attemptNo, j.maxAttempts(), j.createdAt(),
                j.startedAt(), now, j.nextAttemptAt(), workerId, null, null, null, null, null, null, mta, code, msg, null));
        return true;
    }

    @Override
    public synchronized void recordEvent(UUID jobId, String event, int attemptNo, String workerId, String detail) {
        events.add(event);
    }

    @Override
    public synchronized List<Job> findExpiredResults(Instant finishedBefore, int limit) {
        return jobs.values().stream().filter(j -> j.state() == JobState.SUCCEEDED && j.expiredAt() == null
                && j.finishedAt().isBefore(finishedBefore)).limit(limit).collect(Jdk8Compat.toList());
    }

    @Override
    public synchronized void markExpired(UUID jobId) {
        Job j = jobs.get(jobId);
        jobs.put(jobId, new Job(j.jobId(), j.type(), j.jobCode(), j.clientId(), j.requestId(), j.requestHash(),
                j.payloadJson(), j.subjectMst(), j.state(), j.attemptNo(), j.maxAttempts(), j.createdAt(), j.startedAt(),
                j.finishedAt(), j.nextAttemptAt(), j.workerId(), j.leaseUntil(), j.resultBucket(), j.resultPrefix(),
                j.resultUrl(), j.resultManifestJson(), j.rowCount(), j.description(), j.errorCode(), j.errorMessage(), now));
    }
}
