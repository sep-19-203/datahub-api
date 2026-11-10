package vn.teca.hddt.dataapi.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;

/** Kho job bền vững (DSE keyspace datahub_hddt, LWT). Mọi chuyển trạng thái của worker có rào (worker_id + attempt_no). */
public interface JobRepository {

    /** Chèn nếu (client_id, request_id) chưa tồn tại; trả rỗng nếu trùng (idempotency). */
    Optional<Job> insertIfAbsent(NewJob job);

    Optional<Job> findById(UUID jobId);

    Optional<Job> findByClientRequest(String clientId, String requestId);

    long countActiveByClient(String clientId);

    long countActiveBySubject(String subjectMst);

    long countRunning(JobType type);

    /** Nhận 1 job sẵn sàng (QUEUED/RETRY_WAIT đến hạn hoặc RUNNING hết lease) bằng CAS (LWT) — không hai worker cùng nhận. */
    Optional<Job> claimNext(String workerId, Collection<JobType> types, Duration lease);

    boolean renewLease(UUID jobId, String workerId, int attemptNo, Duration lease, String progressJson);

    static final class Completion {
        private final String resultBucket;
        private final String resultPrefix;
        private final String resultUrl;
        private final String manifestJson;
        private final Long rowCount;
        private final String description;

        public Completion(String resultBucket, String resultPrefix, String resultUrl, String manifestJson, Long rowCount,
                String description) {
            this.resultBucket = resultBucket;
            this.resultPrefix = resultPrefix;
            this.resultUrl = resultUrl;
            this.manifestJson = manifestJson;
            this.rowCount = rowCount;
            this.description = description;
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

        public String manifestJson() {
            return manifestJson;
        }

        public Long rowCount() {
            return rowCount;
        }

        public String description() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Completion)) {
                return false;
            }
            Completion other = (Completion) o;
            return Objects.equals(resultBucket, other.resultBucket)
                    && Objects.equals(resultPrefix, other.resultPrefix)
                    && Objects.equals(resultUrl, other.resultUrl)
                    && Objects.equals(manifestJson, other.manifestJson)
                    && Objects.equals(rowCount, other.rowCount)
                    && Objects.equals(description, other.description);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(resultBucket);
            result = 31 * result + Objects.hashCode(resultPrefix);
            result = 31 * result + Objects.hashCode(resultUrl);
            result = 31 * result + Objects.hashCode(manifestJson);
            result = 31 * result + Objects.hashCode(rowCount);
            result = 31 * result + Objects.hashCode(description);
            return result;
        }

        @Override
        public String toString() {
            return "Completion[resultBucket=" + resultBucket
                    + ", resultPrefix=" + resultPrefix
                    + ", resultUrl=" + resultUrl
                    + ", manifestJson=" + manifestJson
                    + ", rowCount=" + rowCount
                    + ", description=" + description + "]";
        }
    }

    boolean markSucceeded(UUID jobId, String workerId, int attemptNo, Completion completion);

    boolean markRetry(UUID jobId, String workerId, int attemptNo, Instant nextAttemptAt, String errorCode, String errorMessage);

    boolean markFailed(UUID jobId, String workerId, int attemptNo, String errorCode, String errorMessage, String description);

    void recordEvent(UUID jobId, String event, int attemptNo, String workerId, String detail);

    /** Job HOAN_THANH quá hạn lưu trữ, chưa đánh dấu hết hạn. */
    java.util.List<Job> findExpiredResults(Instant finishedBefore, int limit);

    void markExpired(UUID jobId);
}
