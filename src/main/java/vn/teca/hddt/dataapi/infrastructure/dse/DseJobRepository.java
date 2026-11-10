package vn.teca.hddt.dataapi.infrastructure.dse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;

import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;

/**
 * Kho job trên DSE (keyspace datahub_hddt). Mọi điểm cạnh tranh dùng LWT (Paxos):
 * <ul>
 * <li>Idempotency: {@code api_job_by_request INSERT ... IF NOT EXISTS} — job chỉ "được xác nhận" khi có ánh xạ này.</li>
 * <li>Nhận job: {@code UPDATE api_job ... IF state = ? AND attempt_no = ? [AND lease_until = ?]} — CAS trên trạng thái đã đọc.</li>
 * <li>Rào: gia hạn/kết thúc {@code IF worker_id = ? AND attempt_no = ? AND state = 'RUNNING'}.</li>
 * </ul>
 * Mọi ghi vào {@code api_job} đều là LWT (kể cả lúc tạo) để thứ tự ghi do Paxos quyết định — không trộn timestamp client
 * với timestamp Paxos (lỗi "LWT bị đè" khi đồng hồ client nhanh hơn server, đã tái hiện trên DSE DEV).
 * Thời gian lease theo đồng hồ ứng dụng (UTC, NTP) — CQL không so sánh được với now() trong điều kiện LWT.
 * {@code hddt.dse.active-shards} không được đổi khi còn job chưa kết thúc (shard = hash(jobId) mod N).
 */
@Repository
public class DseJobRepository implements JobRepository {
    private static final Logger log = LoggerFactory.getLogger(DseJobRepository.class);
    private static final Duration ORPHAN_AGE = Duration.ofMinutes(10);
    private static final long SNAPSHOT_MILLIS = 500;

    private final CqlSession s;
    private final int shards;
    private final Clock clock;
    private final PreparedStatement insertJob;
    private final PreparedStatement insertByRequest;
    private final PreparedStatement insertActive;
    private final PreparedStatement deleteActive;
    private final PreparedStatement deleteJob;
    private final PreparedStatement selectJob;
    private final PreparedStatement selectByRequest;
    private final PreparedStatement selectActive;
    private final PreparedStatement claimReady;
    private final PreparedStatement claimExpired;
    private final PreparedStatement renewWithProgress;
    private final PreparedStatement renewOnly;
    private final PreparedStatement succeed;
    private final PreparedStatement retry;
    private final PreparedStatement fail;
    private final PreparedStatement insertEvent;
    private final PreparedStatement insertFinished;
    private final PreparedStatement selectFinished;
    private final PreparedStatement expire;

    private volatile Snapshot snapshot;

    static final class Snapshot {
        private final long takenAtMillis;
        private final List<Job> active;

        Snapshot(long takenAtMillis, List<Job> active) {
            this.takenAtMillis = takenAtMillis;
            this.active = active;
        }

        public long takenAtMillis() {
            return takenAtMillis;
        }

        public List<Job> active() {
            return active;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Snapshot)) {
                return false;
            }
            Snapshot other = (Snapshot) o;
            return takenAtMillis == other.takenAtMillis
                    && Objects.equals(active, other.active);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(takenAtMillis);
            result = 31 * result + Objects.hashCode(active);
            return result;
        }

        @Override
        public String toString() {
            return "Snapshot[takenAtMillis=" + takenAtMillis
                    + ", active=" + active + "]";
        }
    }

    @Autowired
    public DseJobRepository(CqlSession jobStoreSession, HddtProperties props) {
        this(jobStoreSession, props.dse().keyspace(), props.dse().activeShards(), Clock.systemUTC());
    }

    public DseJobRepository(CqlSession session, String keyspace, int shards, Clock clock) {
        this.s = session;
        this.shards = shards;
        this.clock = clock;
        String k = keyspace + ".";
        String fence = " WHERE job_id = ? IF worker_id = ? AND attempt_no = ? AND state = 'RUNNING'";
        insertJob = s.prepare("INSERT INTO " + k + "api_job (job_id, shard, job_type, job_code, client_id, request_id,"
                + " request_hash, request_payload, subject_mst, state, attempt_no, max_attempts, created_at, updated_at,"
                + " next_attempt_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, ?, ?, ?, ?) IF NOT EXISTS");
        insertByRequest = s.prepare("INSERT INTO " + k + "api_job_by_request (client_id, request_id, job_id, created_at)"
                + " VALUES (?, ?, ?, ?) IF NOT EXISTS");
        insertActive = s.prepare("INSERT INTO " + k + "api_job_active (shard, job_id, job_type, client_id, subject_mst,"
                + " created_at) VALUES (?, ?, ?, ?, ?, ?)");
        deleteActive = s.prepare("DELETE FROM " + k + "api_job_active WHERE shard = ? AND job_id = ?");
        deleteJob = s.prepare("DELETE FROM " + k + "api_job WHERE job_id = ? IF EXISTS");
        selectJob = s.prepare("SELECT * FROM " + k + "api_job WHERE job_id = ?");
        selectByRequest = s.prepare("SELECT job_id FROM " + k + "api_job_by_request WHERE client_id = ? AND request_id = ?");
        selectActive = s.prepare("SELECT job_id, job_type, created_at FROM " + k + "api_job_active WHERE shard = ?");
        String claimSet = "UPDATE " + k + "api_job SET state = 'RUNNING', worker_id = ?, lease_until = ?, attempt_no = ?,"
                + " started_at = ?, updated_at = ? WHERE job_id = ?";
        claimReady = s.prepare(claimSet + " IF state = ? AND attempt_no = ?");
        claimExpired = s.prepare(claimSet + " IF state = 'RUNNING' AND attempt_no = ? AND lease_until = ?");
        renewWithProgress = s.prepare("UPDATE " + k + "api_job SET lease_until = ?, progress = ?, updated_at = ?" + fence);
        renewOnly = s.prepare("UPDATE " + k + "api_job SET lease_until = ?, updated_at = ?" + fence);
        succeed = s.prepare("UPDATE " + k + "api_job SET state = 'SUCCEEDED', finished_at = ?, updated_at = ?,"
                + " lease_until = null, result_bucket = ?, result_prefix = ?, result_url = ?, result_manifest = ?,"
                + " row_count = ?, description = ?, error_code = null, error_message = null" + fence);
        retry = s.prepare("UPDATE " + k + "api_job SET state = 'RETRY_WAIT', next_attempt_at = ?, worker_id = null,"
                + " lease_until = null, updated_at = ?, error_code = ?, error_message = ?" + fence);
        fail = s.prepare("UPDATE " + k + "api_job SET state = 'FAILED', finished_at = ?, updated_at = ?, lease_until = null,"
                + " error_code = ?, error_message = ?, description = ?" + fence);
        insertEvent = s.prepare("INSERT INTO " + k + "api_job_event (job_id, event_id, event, attempt_no, worker_id, detail)"
                + " VALUES (?, ?, ?, ?, ?, ?)");
        insertFinished = s.prepare("INSERT INTO " + k + "api_job_finished (day, finished_at, job_id) VALUES (?, ?, ?)");
        selectFinished = s.prepare("SELECT job_id FROM " + k + "api_job_finished WHERE day = ? AND finished_at < ?");
        expire = s.prepare("UPDATE " + k + "api_job SET expired_at = ?, updated_at = ? WHERE job_id = ? IF EXISTS");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    int shardOf(UUID jobId) {
        return Math.floorMod(jobId.hashCode(), shards);
    }

    // ------------------------------------------------------------------ tạo / đọc

    @Override
    public Optional<Job> insertIfAbsent(NewJob j) {
        Instant now = now();
        int shard = shardOf(j.jobId());
        if (!s.execute(insertJob.bind(j.jobId(), shard, j.type().name(), j.jobCode(), j.clientId(), j.requestId(),
                j.requestHash(), j.payloadJson(), j.subjectMst(), j.maxAttempts(), now, now, now)).wasApplied()) {
            throw new IllegalStateException("Trùng job_id " + j.jobId());
        }
        s.execute(insertActive.bind(shard, j.jobId(), j.type().name(), j.clientId(), j.subjectMst(), now));
        boolean applied;
        try {
            applied = s.execute(insertByRequest.bind(j.clientId(), j.requestId(), j.jobId(), now)).wasApplied();
        } catch (DriverException e) {
            // Kết quả LWT không rõ: đọc tuần tự hóa để biết ánh xạ đã thuộc về job này chưa.
            applied = committedJobId(j.clientId(), j.requestId(), true).map(j.jobId()::equals).orElse(false);
        }
        if (applied) {
            return findById(j.jobId());
        }
        s.execute(deleteActive.bind(shard, j.jobId()));
        s.execute(deleteJob.bind(j.jobId()));
        return Optional.empty();
    }

    private Optional<UUID> committedJobId(String clientId, String requestId, boolean serial) {
        BoundStatement st = selectByRequest.bind(clientId, requestId);
        if (serial) {
            st = st.setConsistencyLevel(DefaultConsistencyLevel.LOCAL_SERIAL);
        }
        Row r = s.execute(st).one();
        return r == null ? Optional.empty() : Optional.ofNullable(r.getUuid("job_id"));
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        Row r = s.execute(selectJob.bind(jobId)).one();
        return r == null || r.getString("state") == null ? Optional.empty() : Optional.of(map(r));
    }

    private Optional<Job> findByIdSerial(UUID jobId) {
        Row r = s.execute(selectJob.bind(jobId).setConsistencyLevel(DefaultConsistencyLevel.LOCAL_SERIAL)).one();
        return r == null || r.getString("state") == null ? Optional.empty() : Optional.of(map(r));
    }

    @Override
    public Optional<Job> findByClientRequest(String clientId, String requestId) {
        return committedJobId(clientId, requestId, false).flatMap(this::findById);
    }

    static Job map(Row r) {
        return new Job(r.getUuid("job_id"), JobType.valueOf(r.getString("job_type")), r.getString("job_code"),
                r.getString("client_id"), r.getString("request_id"), r.getString("request_hash"),
                r.getString("request_payload"), r.getString("subject_mst"), JobState.valueOf(r.getString("state")),
                r.getInt("attempt_no"), r.getInt("max_attempts"), r.getInstant("created_at"), r.getInstant("started_at"),
                r.getInstant("finished_at"), r.getInstant("next_attempt_at"), r.getString("worker_id"),
                r.getInstant("lease_until"), r.getString("result_bucket"), r.getString("result_prefix"),
                r.getString("result_url"), r.getString("result_manifest"),
                r.isNull("row_count") ? null : r.getLong("row_count"), r.getString("description"),
                r.getString("error_code"), r.getString("error_message"), r.getInstant("expired_at"));
    }

    // ------------------------------------------------------------------ chỉ mục job chưa kết thúc

    private static final class ActiveRow {
        private final int shard;
        private final UUID jobId;
        private final String type;
        private final Instant createdAt;

        private ActiveRow(int shard, UUID jobId, String type, Instant createdAt) {
            this.shard = shard;
            this.jobId = jobId;
            this.type = type;
            this.createdAt = createdAt;
        }

        public int shard() {
            return shard;
        }

        public UUID jobId() {
            return jobId;
        }

        public String type() {
            return type;
        }

        public Instant createdAt() {
            return createdAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ActiveRow)) {
                return false;
            }
            ActiveRow other = (ActiveRow) o;
            return shard == other.shard
                    && Objects.equals(jobId, other.jobId)
                    && Objects.equals(type, other.type)
                    && Objects.equals(createdAt, other.createdAt);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(shard);
            result = 31 * result + Objects.hashCode(jobId);
            result = 31 * result + Objects.hashCode(type);
            result = 31 * result + Objects.hashCode(createdAt);
            return result;
        }

        @Override
        public String toString() {
            return "ActiveRow[shard=" + shard
                    + ", jobId=" + jobId
                    + ", type=" + type
                    + ", createdAt=" + createdAt + "]";
        }
    }

    private List<ActiveRow> scanActive() {
        List<ActiveRow> out = new ArrayList<>();
        for (int sh = 0; sh < shards; sh++) {
            for (Row r : s.execute(selectActive.bind(sh))) {
                out.add(new ActiveRow(sh, r.getUuid("job_id"), r.getString("job_type"), r.getInstant("created_at")));
            }
        }
        return out;
    }

    /** Ảnh chụp job chưa kết thúc (dọn dòng chỉ mục của job đã kết thúc / mồ côi), cache ngắn cho các phép đếm. */
    private List<Job> activeJobs() {
        Snapshot snap = snapshot;
        long nowMs = clock.millis();
        if (snap != null && nowMs - snap.takenAtMillis() < SNAPSHOT_MILLIS) {
            return snap.active();
        }
        List<Job> jobs = new ArrayList<>();
        for (ActiveRow a : scanActive()) {
            Optional<Job> j = findById(a.jobId());
            if (!j.isPresent() || !j.get().state().isActive()) {
                if (!j.isPresent() && a.createdAt() != null && a.createdAt().isAfter(now().minus(ORPHAN_AGE))) {
                    continue; // có thể đang được tạo
                }
                s.execute(deleteActive.bind(a.shard(), a.jobId()));
                continue;
            }
            jobs.add(j.get());
        }
        snapshot = new Snapshot(nowMs, Jdk8Compat.listCopyOf(jobs));
        return jobs;
    }

    @Override
    public long countActiveByClient(String clientId) {
        return activeJobs().stream().filter(j -> j.clientId().equals(clientId)).count();
    }

    @Override
    public long countActiveBySubject(String subjectMst) {
        return activeJobs().stream().filter(j -> subjectMst.equals(j.subjectMst())).count();
    }

    @Override
    public long countRunning(JobType type) {
        Instant now = now();
        return activeJobs().stream().filter(j -> j.type() == type && j.state() == JobState.RUNNING
                && j.leaseUntil() != null && j.leaseUntil().isAfter(now)).count();
    }

    // ------------------------------------------------------------------ nhận job

    @Override
    public Optional<Job> claimNext(String workerId, Collection<JobType> types, Duration lease) {
        if (types.isEmpty()) {
            return Optional.empty();
        }
        List<String> wanted = types.stream().map(Enum::name).collect(Jdk8Compat.toList());
        List<ActiveRow> candidates = scanActive().stream().filter(a -> wanted.contains(a.type()))
                .sorted(Comparator.comparing(ActiveRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Jdk8Compat.toList());
        Instant now = now();
        for (ActiveRow a : candidates) {
            Optional<Job> cur = findById(a.jobId());
            if (!cur.isPresent()) {
                continue;
            }
            Job j = cur.get();
            if (!j.state().isActive()) {
                s.execute(deleteActive.bind(a.shard(), a.jobId()));
                continue;
            }
            boolean ready = (j.state() == JobState.QUEUED || j.state() == JobState.RETRY_WAIT)
                    && !j.nextAttemptAt().isAfter(now);
            boolean expired = j.state() == JobState.RUNNING && j.leaseUntil() != null && j.leaseUntil().isBefore(now);
            if (!ready && !expired) {
                continue;
            }
            if (!isCommitted(j, now)) {
                continue;
            }
            int attempt = j.attemptNo() + 1;
            Instant started = j.startedAt() == null ? now : j.startedAt();
            Instant leaseUntil = now.plus(lease);
            try {
                ResultSet rs = ready
                        ? s.execute(claimReady.bind(workerId, leaseUntil, attempt, started, now, j.jobId(), j.state().name(), j.attemptNo()))
                        : s.execute(claimExpired.bind(workerId, leaseUntil, attempt, started, now, j.jobId(), j.attemptNo(), j.leaseUntil()));
                if (rs.wasApplied()) {
                    return findById(j.jobId());
                }
            } catch (DriverException e) {
                Optional<Job> after = findByIdSerial(j.jobId());
                if (after.isPresent() && after.get().state() == JobState.RUNNING && workerId.equals(after.get().workerId())
                        && after.get().attemptNo() == attempt) {
                    return after;
                }
                log.warn("Nhận job {} không xác định được kết quả LWT: {}", j.jobId(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    /** Job chỉ hợp lệ khi ánh xạ idempotency trỏ về nó; job mồ côi (tạo dở) quá 10 phút thì dọn. */
    private boolean isCommitted(Job j, Instant now) {
        Optional<UUID> owner = committedJobId(j.clientId(), j.requestId(), false);
        if (owner.isPresent() && owner.get().equals(j.jobId())) {
            return true;
        }
        if (j.createdAt() != null && j.createdAt().isBefore(now.minus(ORPHAN_AGE))) {
            s.execute(deleteActive.bind(shardOf(j.jobId()), j.jobId()));
            s.execute(deleteJob.bind(j.jobId()));
            log.warn("Dọn job mồ côi {} (không có ánh xạ X-Request-Id)", j.jobId());
        }
        return false;
    }

    // ------------------------------------------------------------------ chuyển trạng thái có rào

    @Override
    public boolean renewLease(UUID jobId, String workerId, int attemptNo, Duration lease, String progressJson) {
        Instant now = now();
        ResultSet rs = progressJson == null
                ? s.execute(renewOnly.bind(now.plus(lease), now, jobId, workerId, attemptNo))
                : s.execute(renewWithProgress.bind(now.plus(lease), progressJson, now, jobId, workerId, attemptNo));
        return rs.wasApplied();
    }

    @Override
    public boolean markSucceeded(UUID jobId, String workerId, int attemptNo, Completion c) {
        Instant now = now();
        boolean ok = s.execute(succeed.bind(now, now, c.resultBucket(), c.resultPrefix(), c.resultUrl(), c.manifestJson(),
                c.rowCount(), c.description(), jobId, workerId, attemptNo)).wasApplied();
        if (ok) {
            s.execute(deleteActive.bind(shardOf(jobId), jobId));
            s.execute(insertFinished.bind(now.atZone(ZoneOffset.UTC).toLocalDate(), now, jobId));
        }
        return ok;
    }

    @Override
    public boolean markRetry(UUID jobId, String workerId, int attemptNo, Instant nextAttemptAt, String errorCode,
                             String errorMessage) {
        return s.execute(retry.bind(nextAttemptAt.truncatedTo(ChronoUnit.MILLIS), now(), errorCode, errorMessage, jobId,
                workerId, attemptNo)).wasApplied();
    }

    @Override
    public boolean markFailed(UUID jobId, String workerId, int attemptNo, String errorCode, String errorMessage,
                              String description) {
        Instant now = now();
        boolean ok = s.execute(fail.bind(now, now, errorCode, errorMessage, description, jobId, workerId, attemptNo))
                .wasApplied();
        if (ok) {
            s.execute(deleteActive.bind(shardOf(jobId), jobId));
        }
        return ok;
    }

    @Override
    public void recordEvent(UUID jobId, String event, int attemptNo, String workerId, String detail) {
        s.execute(insertEvent.bind(jobId, Uuids.timeBased(), event, attemptNo, workerId, detail));
    }

    @Override
    public List<Job> findExpiredResults(Instant finishedBefore, int limit) {
        List<Job> out = new ArrayList<>();
        LocalDate last = finishedBefore.atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = last.minusDays(60); !d.isAfter(last) && out.size() < limit; d = d.plusDays(1)) {
            for (Row r : s.execute(selectFinished.bind(d, finishedBefore))) {
                findById(r.getUuid("job_id")).filter(j -> j.state() == JobState.SUCCEEDED && j.expiredAt() == null)
                        .ifPresent(out::add);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public void markExpired(UUID jobId) {
        Instant now = now();
        s.execute(expire.bind(now, now, jobId));
    }
}
