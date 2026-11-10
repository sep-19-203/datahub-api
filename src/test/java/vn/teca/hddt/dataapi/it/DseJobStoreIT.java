package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.RefundStorePort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.infrastructure.dse.DseConfig;
import vn.teca.hddt.dataapi.infrastructure.dse.DseJobRepository;
import vn.teca.hddt.dataapi.infrastructure.dse.DseRefundStore;
import vn.teca.hddt.dataapi.support.TestProps;

/**
 * Kho job thật trên DSE DEV, keyspace TẠM {@code datahub_hddt_it_<rand>} (tạo bằng đúng script cql/V1__job_store.cql, xóa
 * sau test): idempotency LWT, 8 luồng tranh nhận job, lease hết hạn + rào, RETRY_WAIT, đọc lại sau "restart",
 * chống ghi sổ hoàn trùng nội dung.
 */
class DseJobStoreIT {
    static CqlSession session;
    static DseJobRepository repo;
    static HddtProperties.Dse dse;
    static final String KS = "datahub_hddt_it_" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void schema() {
        session = ItInfra.dse();
        String dc = ItInfra.env("IT_DSE_LOCAL_DC") == null ? "DC1" : ItInfra.env("IT_DSE_LOCAL_DC");
        long nodesInDc = session.getMetadata().getNodes().values().stream().filter(n -> dc.equals(n.getDatacenter())).count();
        String replication = "{'class': 'NetworkTopologyStrategy', '" + dc + "': '" + Math.min(3, nodesInDc) + "'}";
        System.out.println("DSE IT: dc=" + dc + " nodesInDc=" + nodesInDc + " keyspace=" + KS);
        dse = new HddtProperties.Dse(Jdk8Compat.listOf(), dc, null, null, KS, replication, true, 3600, 4,
                Duration.ofSeconds(15), Duration.ofSeconds(10));
        DseConfig.installSchema(session, dse);
        repo = new DseJobRepository(session, KS, 4, Clock.systemUTC());
    }

    @AfterAll
    static void drop() {
        if (session != null) {
            session.execute("DROP KEYSPACE IF EXISTS " + KS);
            session.close();
        }
    }

    static NewJob newJob(String client, String req, JobType type) {
        return new NewJob(UUID.randomUUID(), type, type.defaultCode(), client, req, String.join("", java.util.Collections.nCopies(64, "a")),
                "{\"mst\":\"0100000001\"}", "0100000001", 3);
    }

    @Test
    void insertIsIdempotentPerClientAndRequestId_viaLwt() {
        String req = UUID.randomUUID().toString();
        Optional<Job> first = repo.insertIfAbsent(newJob("c1", req, JobType.PURCHASE_LIST));
        assertThat(first).isPresent();
        assertThat(repo.insertIfAbsent(newJob("c1", req, JobType.PURCHASE_LIST))).isEmpty();
        assertThat(repo.insertIfAbsent(newJob("c2", req, JobType.PURCHASE_LIST))).isPresent();
        Job j = repo.findByClientRequest("c1", req).get();
        assertThat(j.jobId()).isEqualTo(first.get().jobId());
        assertThat(j.state()).isEqualTo(JobState.QUEUED);
        assertThat(j.payloadJson()).contains("0100000001");
        assertThat(repo.countActiveByClient("c1")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void concurrentWorkersClaimEachJobExactlyOnce() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            ids.add(repo.insertIfAbsent(newJob("conc", UUID.randomUUID().toString(), JobType.REFUND_UPDATE)).get().jobId());
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<UUID> claimed = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> fs = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            String worker = "w" + w;
            fs.add(pool.submit(() -> {
                Optional<Job> j;
                while ((j = repo.claimNext(worker, Jdk8Compat.listOf(JobType.REFUND_UPDATE), Duration.ofMinutes(5))).isPresent()) {
                    claimed.add(j.get().jobId());
                }
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();
        Set<UUID> unique = new HashSet<>(claimed);
        assertThat(claimed).as("không job nào bị nhận 2 lần").hasSameSizeAs(unique);
        assertThat(unique).containsAll(ids);
    }

    @Test
    void expiredLeaseIsReclaimed_andStaleWorkerCannotPublish() throws Exception {
        UUID id = repo.insertIfAbsent(newJob("lease", UUID.randomUUID().toString(), JobType.SALES_LIST)).get().jobId();
        Job first = claim(id, "pod-a", Duration.ofMillis(1));
        Thread.sleep(50);
        Job second = claim(id, "pod-b", Duration.ofMinutes(5));
        assertThat(second.attemptNo()).isEqualTo(first.attemptNo() + 1);
        Completion c = new Completion("hddt", "p/", "s3://hddt/p/x_*", "{\"rowCount\":1}", 1L, "Thành công");
        assertThat(repo.renewLease(id, "pod-a", first.attemptNo(), Duration.ofMinutes(1), null)).isFalse();
        assertThat(repo.markSucceeded(id, "pod-a", first.attemptNo(), c)).isFalse();
        assertThat(repo.renewLease(id, "pod-b", second.attemptNo(), Duration.ofMinutes(1), "{\"phase\":\"X\"}")).isTrue();
        assertThat(repo.markSucceeded(id, "pod-b", second.attemptNo(), c)).isTrue();
        Job done = repo.findById(id).get();
        assertThat(done.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(done.resultUrl()).isEqualTo("s3://hddt/p/x_*");
        assertThat(done.rowCount()).isEqualTo(1L);
        assertThat(repo.markFailed(id, "pod-b", second.attemptNo(), "X", "x", "x")).isFalse();
        assertThat(repo.findExpiredResults(Instant.now().plusSeconds(5), 100)).extracting(Job::jobId).contains(id);
        repo.markExpired(id);
        assertThat(repo.findById(id).get().expiredAt()).isNotNull();
    }

    private Job claim(UUID id, String worker, Duration lease) {
        for (int i = 0; i < 200; i++) {
            Optional<Job> j = repo.claimNext(worker, Jdk8Compat.listOf(JobType.SALES_LIST), lease);
            if (!j.isPresent()) {
                break;
            }
            if (j.get().jobId().equals(id)) {
                return j.get();
            }
            repo.markRetry(j.get().jobId(), worker, j.get().attemptNo(), Instant.now(), null, null);
        }
        throw new AssertionError("không nhận được job " + id);
    }

    @Test
    void retryWaitIsNotClaimableBeforeNextAttempt_andSurvivesRestart() {
        UUID id = repo.insertIfAbsent(newJob("retry", UUID.randomUUID().toString(), JobType.PURCHASE_VERIFY)).get().jobId();
        Job j = repo.claimNext("pod-a", Jdk8Compat.listOf(JobType.PURCHASE_VERIFY), Duration.ofMinutes(5)).get();
        assertThat(j.jobId()).isEqualTo(id);
        assertThat(repo.markRetry(id, "pod-a", j.attemptNo(), Instant.now().plusSeconds(3600), "HDDT-503", "doris down")).isTrue();
        assertThat(repo.claimNext("pod-b", Jdk8Compat.listOf(JobType.PURCHASE_VERIFY), Duration.ofMinutes(5))).isEmpty();
        Job reloaded = new DseJobRepository(session, KS, 4, Clock.systemUTC()).findById(id).get();
        assertThat(reloaded.state()).isEqualTo(JobState.RETRY_WAIT);
        assertThat(reloaded.publicStatus().name()).isEqualTo("DANG_XU_LY");
        assertThat(reloaded.errorCode()).isEqualTo("HDDT-503");
        repo.recordEvent(id, "RETRY_SCHEDULED", 1, "pod-a", "doris down");
        Row ev = session.execute("SELECT event FROM " + KS + ".api_job_event WHERE job_id = ?", id).one();
        assertThat(ev.getString("event")).isEqualTo("RETRY_SCHEDULED");
    }

    @Test
    void refundStore_stagesItemsPerAttempt_readsValidChunks_andReservesContentOnce() {
        HddtProperties props = TestProps.defaults();
        HddtProperties withKs = new HddtProperties(props.api(), props.doris(), dse, props.s3(), props.mstStatus(), props.hd1(), props.hd5(),
                props.hd7(), props.hd8(), props.tms(), props.export(), props.worker(), props.security(), Jdk8Compat.mapOf());
        DseRefundStore store = new DseRefundStore(session, withKs);
        String sha = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.startBatch(a, 1, "s3://hddt/import/tms/a.csv");
        List<RefundStorePort.Item> items = new ArrayList<>();
        for (long line = 1; line <= 10_003; line += 5_000) { // dòng 1, 5001, 10001 ⇒ bucket 0, 0, 1
            items.add(new RefundStorePort.Item(line, "0100000002", 1, "C26TAA", line, LocalDate.of(2026, 9, 1),
                    RefundStorePort.ItemStatus.VALID, null, "INV", "id-" + line, "0100", "0100000001",
                    new java.math.BigDecimal("940000.000000"), new java.math.BigDecimal("75200"), "[{\"tsuat\":\"8%\"}]", "00", 1,
                    java.time.LocalDateTime.of(2026, 9, 1, 10, 0)));
        }
        items.add(RefundStorePort.Item.rejected(2, RefundStorePort.ItemStatus.INVALID, "lỗi"));
        store.saveItems(a, 1, items);
        List<RefundStorePort.Item> valid = new ArrayList<>();
        List<Integer> chunkSizes = new ArrayList<>();
        store.forEachValidItem(a, 1, 10_003, 2, chunk -> {
            chunkSizes.add(chunk.size());
            valid.addAll(chunk);
        });
        assertThat(valid).extracting(RefundStorePort.Item::lineNo).containsExactly(1L, 5001L, 10001L);
        assertThat(chunkSizes).containsExactly(2, 1);
        assertThat(valid.get(0).nmmst()).isEqualTo("0100000001");
        assertThat(valid.get(0).tgtcthue()).isEqualByComparingTo("940000");
        assertThat(valid.get(0).nky()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 1, 10, 0));
        List<RefundStorePort.Item> otherAttempt = new ArrayList<>();
        store.forEachValidItem(a, 2, 10_003, 10, otherAttempt::addAll);
        assertThat(otherAttempt).isEmpty();                            // lần thử khác không thấy dòng của lần 1
        store.finishValidation(a, sha, 1, 100, new RefundStorePort.BatchCounters(4, 3, 1, 0, 0, 0, 0),
                RefundStorePort.Stage.VALIDATED);
        assertThat(store.reserveContent(a, sha)).isEmpty();
        assertThat(store.reserveContent(a, sha)).isEmpty();           // giữ chỗ lại của chính mình: hợp lệ
        assertThat(store.reserveContent(b, sha)).contains(a);         // job khác cùng nội dung: bị chặn
        store.updateStage(a, RefundStorePort.Stage.APPLIED);
        Row batch = session.execute("SELECT stage, already_refunded_count FROM " + KS + ".refund_batch WHERE job_id = ?", a).one();
        assertThat(batch.getString("stage")).isEqualTo("APPLIED");
        assertThat(batch.getLong("already_refunded_count")).isZero();
    }
}
