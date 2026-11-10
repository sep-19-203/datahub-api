package vn.teca.hddt.dataapi.domain.job;

import java.util.Objects;
import java.util.UUID;

/** Dữ liệu tạo job mới. */
public final class NewJob {
    private final UUID jobId;
    private final JobType type;
    private final String jobCode;
    private final String clientId;
    private final String requestId;
    private final String requestHash;
    private final String payloadJson;
    private final String subjectMst;
    private final int maxAttempts;

    public NewJob(UUID jobId, JobType type, String jobCode, String clientId, String requestId, String requestHash,
            String payloadJson, String subjectMst, int maxAttempts) {
        this.jobId = jobId;
        this.type = type;
        this.jobCode = jobCode;
        this.clientId = clientId;
        this.requestId = requestId;
        this.requestHash = requestHash;
        this.payloadJson = payloadJson;
        this.subjectMst = subjectMst;
        this.maxAttempts = maxAttempts;
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

    public int maxAttempts() {
        return maxAttempts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NewJob)) {
            return false;
        }
        NewJob other = (NewJob) o;
        return Objects.equals(jobId, other.jobId)
                && Objects.equals(type, other.type)
                && Objects.equals(jobCode, other.jobCode)
                && Objects.equals(clientId, other.clientId)
                && Objects.equals(requestId, other.requestId)
                && Objects.equals(requestHash, other.requestHash)
                && Objects.equals(payloadJson, other.payloadJson)
                && Objects.equals(subjectMst, other.subjectMst)
                && maxAttempts == other.maxAttempts;
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
        result = 31 * result + Integer.hashCode(maxAttempts);
        return result;
    }

    @Override
    public String toString() {
        return "NewJob[jobId=" + jobId
                + ", type=" + type
                + ", jobCode=" + jobCode
                + ", clientId=" + clientId
                + ", requestId=" + requestId
                + ", requestHash=" + requestHash
                + ", payloadJson=" + payloadJson
                + ", subjectMst=" + subjectMst
                + ", maxAttempts=" + maxAttempts + "]";
    }
}
