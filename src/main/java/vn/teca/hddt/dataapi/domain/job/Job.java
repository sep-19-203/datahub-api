package vn.teca.hddt.dataapi.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Bản ghi job bền vững (bảng api_job). */
public final class Job {
    private final UUID jobId;
    private final JobType type;
    private final String jobCode;
    private final String clientId;
    private final String requestId;
    private final String requestHash;
    private final String payloadJson;
    private final String subjectMst;
    private final JobState state;
    private final int attemptNo;
    private final int maxAttempts;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final Instant nextAttemptAt;
    private final String workerId;
    private final Instant leaseUntil;
    private final String resultBucket;
    private final String resultPrefix;
    private final String resultUrl;
    private final String resultManifestJson;
    private final Long rowCount;
    private final String description;
    private final String errorCode;
    private final String errorMessage;
    private final Instant expiredAt;

    public Job(UUID jobId, JobType type, String jobCode, String clientId, String requestId, String requestHash,
            String payloadJson, String subjectMst, JobState state, int attemptNo, int maxAttempts, Instant createdAt,
            Instant startedAt, Instant finishedAt, Instant nextAttemptAt, String workerId, Instant leaseUntil,
            String resultBucket, String resultPrefix, String resultUrl, String resultManifestJson, Long rowCount,
            String description, String errorCode, String errorMessage, Instant expiredAt) {
        this.jobId = jobId;
        this.type = type;
        this.jobCode = jobCode;
        this.clientId = clientId;
        this.requestId = requestId;
        this.requestHash = requestHash;
        this.payloadJson = payloadJson;
        this.subjectMst = subjectMst;
        this.state = state;
        this.attemptNo = attemptNo;
        this.maxAttempts = maxAttempts;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.resultBucket = resultBucket;
        this.resultPrefix = resultPrefix;
        this.resultUrl = resultUrl;
        this.resultManifestJson = resultManifestJson;
        this.rowCount = rowCount;
        this.description = description;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.expiredAt = expiredAt;
    }

    public UUID jobId() {
        return jobId;
    }

    public JobType type() {
        return type;
    }

    public String jobCode() {
        return jobCode;
    }

    public String clientId() {
        return clientId;
    }

    public String requestId() {
        return requestId;
    }

    public String requestHash() {
        return requestHash;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public String subjectMst() {
        return subjectMst;
    }

    public JobState state() {
        return state;
    }

    public int attemptNo() {
        return attemptNo;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String workerId() {
        return workerId;
    }

    public Instant leaseUntil() {
        return leaseUntil;
    }

    public String resultBucket() {
        return resultBucket;
    }

    public String resultPrefix() {
        return resultPrefix;
    }

    public String resultUrl() {
        return resultUrl;
    }

    public String resultManifestJson() {
        return resultManifestJson;
    }

    public Long rowCount() {
        return rowCount;
    }

    public String description() {
        return description;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant expiredAt() {
        return expiredAt;
    }

    public PublicJobStatus publicStatus() {
        return state.publicStatus();
    }

    /** Chỉ công bố URL/số dòng khi kết quả đã hoàn chỉnh và chưa hết hạn lưu trữ. */
    public boolean resultPublished() {
        return state == JobState.SUCCEEDED && expiredAt == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Job)) {
            return false;
        }
        Job other = (Job) o;
        return Objects.equals(jobId, other.jobId)
                && Objects.equals(type, other.type)
                && Objects.equals(jobCode, other.jobCode)
                && Objects.equals(clientId, other.clientId)
                && Objects.equals(requestId, other.requestId)
                && Objects.equals(requestHash, other.requestHash)
                && Objects.equals(payloadJson, other.payloadJson)
                && Objects.equals(subjectMst, other.subjectMst)
                && Objects.equals(state, other.state)
                && attemptNo == other.attemptNo
                && maxAttempts == other.maxAttempts
                && Objects.equals(createdAt, other.createdAt)
                && Objects.equals(startedAt, other.startedAt)
                && Objects.equals(finishedAt, other.finishedAt)
                && Objects.equals(nextAttemptAt, other.nextAttemptAt)
                && Objects.equals(workerId, other.workerId)
                && Objects.equals(leaseUntil, other.leaseUntil)
                && Objects.equals(resultBucket, other.resultBucket)
                && Objects.equals(resultPrefix, other.resultPrefix)
                && Objects.equals(resultUrl, other.resultUrl)
                && Objects.equals(resultManifestJson, other.resultManifestJson)
                && Objects.equals(rowCount, other.rowCount)
                && Objects.equals(description, other.description)
                && Objects.equals(errorCode, other.errorCode)
                && Objects.equals(errorMessage, other.errorMessage)
                && Objects.equals(expiredAt, other.expiredAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(jobId);
        result = 31 * result + Objects.hashCode(type);
        result = 31 * result + Objects.hashCode(jobCode);
        result = 31 * result + Objects.hashCode(clientId);
        result = 31 * result + Objects.hashCode(requestId);
        result = 31 * result + Objects.hashCode(requestHash);
        result = 31 * result + Objects.hashCode(payloadJson);
        result = 31 * result + Objects.hashCode(subjectMst);
        result = 31 * result + Objects.hashCode(state);
        result = 31 * result + Integer.hashCode(attemptNo);
        result = 31 * result + Integer.hashCode(maxAttempts);
        result = 31 * result + Objects.hashCode(createdAt);
        result = 31 * result + Objects.hashCode(startedAt);
        result = 31 * result + Objects.hashCode(finishedAt);
        result = 31 * result + Objects.hashCode(nextAttemptAt);
        result = 31 * result + Objects.hashCode(workerId);
        result = 31 * result + Objects.hashCode(leaseUntil);
        result = 31 * result + Objects.hashCode(resultBucket);
        result = 31 * result + Objects.hashCode(resultPrefix);
        result = 31 * result + Objects.hashCode(resultUrl);
        result = 31 * result + Objects.hashCode(resultManifestJson);
        result = 31 * result + Objects.hashCode(rowCount);
        result = 31 * result + Objects.hashCode(description);
        result = 31 * result + Objects.hashCode(errorCode);
        result = 31 * result + Objects.hashCode(errorMessage);
        result = 31 * result + Objects.hashCode(expiredAt);
        return result;
    }

    @Override
    public String toString() {
        return "Job[jobId=" + jobId
                + ", type=" + type
                + ", jobCode=" + jobCode
                + ", clientId=" + clientId
                + ", requestId=" + requestId
                + ", requestHash=" + requestHash
                + ", payloadJson=" + payloadJson
                + ", subjectMst=" + subjectMst
                + ", state=" + state
                + ", attemptNo=" + attemptNo
                + ", maxAttempts=" + maxAttempts
                + ", createdAt=" + createdAt
                + ", startedAt=" + startedAt
                + ", finishedAt=" + finishedAt
                + ", nextAttemptAt=" + nextAttemptAt
                + ", workerId=" + workerId
                + ", leaseUntil=" + leaseUntil
                + ", resultBucket=" + resultBucket
                + ", resultPrefix=" + resultPrefix
                + ", resultUrl=" + resultUrl
                + ", resultManifestJson=" + resultManifestJson
                + ", rowCount=" + rowCount
                + ", description=" + description
                + ", errorCode=" + errorCode
                + ", errorMessage=" + errorMessage
                + ", expiredAt=" + expiredAt + "]";
    }
}
