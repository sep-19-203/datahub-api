package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.csv.InvoiceRecordCsv;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobPayloads.RefundParams;
import vn.teca.hddt.dataapi.application.job.JobPayloads.VerifyParams;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.PurchaseCandidates;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.RefundStorePort;
import vn.teca.hddt.dataapi.application.port.RefundLedgerPort;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.application.refund.RefundUpdateHandler;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.application.verify.PurchaseVerificationHandler;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.verify.CompareField;
import vn.teca.hddt.dataapi.domain.verify.ComparisonPolicy;
import vn.teca.hddt.dataapi.support.Fixtures;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;
import vn.teca.hddt.dataapi.support.InMemoryObjectStorage;
import vn.teca.hddt.dataapi.support.TestProps;

class VerificationAndRefundHandlerTest {
    final InMemoryJobRepository repo = new InMemoryJobRepository();
    final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    final TaxpayerStatusPort statuses = mock(TaxpayerStatusPort.class);
    final S3UrlPolicy urlPolicy = new S3UrlPolicy(Jdk8Compat.listOf("hddt"));
    final ComparisonPolicy policy = new ComparisonPolicy(Jdk8Compat.listOf(CompareField.TGTCTHUE, CompareField.TGTTHUE), BigDecimal.ZERO);
    final List<Integer> lookupBatchSizes = new ArrayList<>();

    final List<Integer> indexCalls = new ArrayList<>();

    /**
     * Doris giả: findPurchases = cách cũ (một truy vấn/lô); indexPurchases/fetchPurchases = DB-P1-02 (chỉ mục ứng viên theo
     * khóa tra cứu nbmst+khhdon+shdon trong khoảng TDLAP, rồi tra theo khóa vật lý và lọc lại bằng khóa đủ).
     */
    final class FakeDoris implements InvoiceLookupPort {
        final List<InvoiceRecord> db;

        FakeDoris(List<InvoiceRecord> db) {
            this.db = db;
        }

        @Override
        public List<InvoiceRecord> findPurchases(String nmmst, LocalDate tngay, LocalDate dngay, Collection<InvoiceKey> keys) {
            lookupBatchSizes.add(keys.size());
            return db.stream().filter(r -> keys.contains(r.key()) && !r.tdlap().isBefore(tngay) && !r.tdlap().isAfter(dngay))
                    .collect(Jdk8Compat.toList());
        }

        @Override
        public PurchaseCandidates indexPurchases(String nmmst, LocalDate tngay, LocalDate dngay, LongPredicate wanted) {
            indexCalls.add(1);
            PurchaseCandidates idx = new PurchaseCandidates();
            for (InvoiceRecord r : db) {
                if (wanted.test(r.key().lookupFingerprint()) && !r.tdlap().isBefore(tngay) && !r.tdlap().isAfter(dngay)) {
                    idx.add(r.key().lookupFingerprint(), r.tdlap(), "0100", r.loaiHd(), r.id(), r.tdlap().withDayOfMonth(1));
                }
            }
            return idx;
        }

        @Override
        public long countPurchaseCandidates(String nmmst, LocalDate tngay, LocalDate dngay) {
            return db.stream().filter(r -> !r.tdlap().isBefore(tngay) && !r.tdlap().isAfter(dngay)).count();
        }

        @Override
        public List<InvoiceRecord> fetchPurchases(String nmmst, PurchaseCandidates index, Collection<InvoiceKey> keys) {
            lookupBatchSizes.add(keys.size());
            List<InvoiceRecord> out = new ArrayList<>();
            for (PurchaseCandidates.Ref ref : index.refsFor(keys)) {
                db.stream().filter(r -> r.id().equals(ref.id()) && r.loaiHd().equals(ref.loaiHd()) && r.tdlap().equals(ref.tdlap())
                        && keys.contains(r.key())).forEach(out::add);
            }
            return out;
        }

        @Override
        public List<RefundTarget> findRefundTargets(Collection<DatedKey> keys) {
            lookupBatchSizes.add(keys.size());
            List<InvoiceKey> ks = keys.stream().map(DatedKey::key).collect(Jdk8Compat.toList());
            return db.stream().filter(r -> ks.contains(r.key())).map(r -> new RefundTarget(r.key(), r.tdlap(), r.loaiHd(),
                    "0100", r.id(), Fixtures.BUYER)).collect(Jdk8Compat.toList());
        }
    }

    /** Doris giả chứa 2 HĐ của người bán SELLER: số 1 (đúng), số 2 (tiền khác). */
    final InvoiceLookupPort doris = new FakeDoris(Jdk8Compat.listOf(
            Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000.000000").withTtmstnban(null),
            Fixtures.record(Fixtures.SELLER, "C26TAA", 2, "500000.000000").withTtmstnban(null)));

    HddtProperties props(boolean confirmed) {
        return TestProps.props("SELECT 1", confirmed, HddtProperties.Tms.ApplyMode.BLOCKED, TestProps.fullMapping());
    }

    String input(InvoiceRecord... rs) {
        StringBuilder b = new StringBuilder();
        for (InvoiceRecord r : rs) {
            b.append(InvoiceRecordCsv.line(r)).append('\n');
        }
        return b.toString();
    }

    JobContext hd8Ctx() {
        return HandlerTestSupport.claimed(repo, JobType.PURCHASE_VERIFY, new VerifyParams(Fixtures.BUYER, "2026-09-01",
                "2026-09-30", "s3://hddt/import/tthc/in_*"));
    }

    @Test
    void hd8_v3_returnsOnlyInvalidRows_systemValuesBesideNntValues_inBatches() throws Exception {
        when(statuses.configured()).thenReturn(true);
        when(statuses.findStatuses(any())).thenReturn(Jdk8Compat.mapOf(Fixtures.SELLER, "00"));
        // Doris: HĐ số 2 có tổng hợp thuế suất + lí do điều chỉnh/thay thế (GCHDGOC)
        InvoiceRecord two = Fixtures.record(Fixtures.SELLER, "C26TAA", 2, "500000.000000");
        InvoiceLookupPort dorisV3 = new FakeDoris(Jdk8Compat.listOf(
                Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000.000000").withTtmstnban(null),
                new InvoiceRecord(two.nbmst(), two.khmshdon(), two.khhdon(), two.shdon(), two.tdlap(), two.nky(), two.tthai(),
                        two.nbten(), two.tgtcthue(), two.tgtthue(), null, null, null, null, two.loaiHd(), two.id(),
                        "[{\"tsuat\":\"10%\",\"thtien\":500000,\"tthue\":50000}]", "Thay thế HĐ 1")));
        // File vào = HD1 v3 15 cột. Dòng 1: khớp tiền nhưng tên người bán / tthai khác (không nằm trong quy tắc) ⇒ hợp lệ
        String ok = "0100000002,1,C26TAA,1,2026-09-15,5,TEN KHAC DI,940000,80000,,,,,,";
        // Dòng 2: tgtcthue lệch 1 đồng ⇒ không hợp lệ (viết số không có phần thập phân, MST không bọc nháy)
        String bad = "0100000002,1,C26TAA,2,2026-09-15,1,\"CTY, \"\"B\"\"\",500001,80000,\"[{\"\"tsuat\"\":\"\"10%\"\"}]\",,,,03,";
        // Dòng 3: không tồn tại
        String missing = "0100000002,1,C26TAA,3,2026-09-14,1,CTY C,1,0.1,,,,,,";
        storage.put("hddt", "import/tthc/in_0.csv", ok + "\n" + bad + "\n");
        storage.put("hddt", "import/tthc/in_1.csv", missing + "\n");
        JobContext c = hd8Ctx();
        Completion done = new PurchaseVerificationHandler(storage, dorisV3, statuses, props(true), urlPolicy, policy).execute(c);
        assertThat(done.rowCount()).isEqualTo(2);
        assertThat(done.description()).contains("kiểm tra 3 hóa đơn, lệch 2").contains("không tồn tại 1, sai lệch 1");
        assertThat(lookupBatchSizes).containsExactly(2, 1); // batch-size = 2 ⇒ 2 truy vấn cho 3 dòng, không N+1
        assertThat(indexCalls).hasSize(1); // DB-P1-02: một truy vấn ứng viên cho cả job
        String prefix = "export/hd8-result/" + c.jobId() + "/";
        String out = storage.objects.entrySet().stream().filter(e -> e.getKey().startsWith("hddt/" + prefix))
                .map(e -> new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8)).reduce("", String::concat);
        String[] lines = out.split("\n");
        assertThat(lines).hasSize(2);
        // SI-65: 21 cột; cột không hậu tố = Doris, *_nnt = văn bản NNT gửi (500001 giữ nguyên, không thành 500001.000000);
        // ttmstnban tra theo nbmst; nky + 13 pct* gạch ngang ⇒ không có cột
        assertThat(lines[0]).isEqualTo("\"0100000002\",1,\"C26TAA\",2,2026-09-15,2026-09-15,1,1,"
                + "\"CONG TY GIA, \"\"A\"\"\",\"CTY, \"\"B\"\"\",500000.000000,500001,80000.000000,80000,"
                + "\"[{\"\"tsuat\"\":\"\"10%\"\",\"\"thtien\"\":500000,\"\"tthue\"\":50000}]\",\"[{\"\"tsuat\"\":\"\"10%\"\"}]\","
                + ",,,\"00\",\"Thay thế HĐ 1\"");
        // Không tồn tại: khóa + tdlap lấy từ file (người dùng chốt 08/11), cột hệ thống khác trống, *_nnt từ file
        assertThat(lines[1]).isEqualTo("\"0100000002\",1,\"C26TAA\",3,2026-09-14,2026-09-14,,1,,\"CTY C\",,1,,0.1,,,,,,\"00\",");
        for (String l : lines) {
            assertThat(CsvCodecTest.parse(l + "\n").get(0).size()).isEqualTo(vn.teca.hddt.dataapi.domain.csv.CsvLayouts.HD8_RESULT.size());
        }
        assertThat(done.resultUrl()).startsWith("s3://hddt/" + prefix).endsWith("_*");
    }

    @Test
    void hd8_anyParseErrorFailsWholeJob_noReconciliationConclusion() {
        when(statuses.configured()).thenReturn(true);
        storage.put("hddt", "import/tthc/in_0.csv", input(Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000"))
                + "\"0100000002\",1,\"C26TAA\",xx,2026-09-15\n");
        assertThatThrownBy(() -> new PurchaseVerificationHandler(storage, doris, statuses, props(true), urlPolicy, policy)
                .execute(hd8Ctx())).isInstanceOf(PermanentJobException.class).hasMessageContaining("không thực hiện đối soát");
        assertThat(lookupBatchSizes).isEmpty();
    }

    @Test
    void hd8_duplicateKeysAndOutOfRangeDates_areInputErrors() {
        when(statuses.configured()).thenReturn(true);
        InvoiceRecord r = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        storage.put("hddt", "import/tthc/in_0.csv", input(r, r));
        assertThatThrownBy(() -> new PurchaseVerificationHandler(storage, doris, statuses, props(true), urlPolicy, policy)
                .execute(hd8Ctx())).hasMessageContaining("trùng khóa");
        storage.objects.clear();
        JobContext c = HandlerTestSupport.claimed(repo, JobType.PURCHASE_VERIFY, new VerifyParams(Fixtures.BUYER,
                "2026-10-01", "2026-10-31", "s3://hddt/import/tthc/in_*"));
        storage.put("hddt", "import/tthc/in_0.csv", input(r));
        assertThatThrownBy(() -> new PurchaseVerificationHandler(storage, doris, statuses, props(true), urlPolicy, policy)
                .execute(c)).hasMessageContaining("ngoài khoảng");
    }

    /** Kết quả (mô tả + các dòng ghi ra) của một lần chạy HD8 với {@code p}. */
    String hd8Run(InvoiceLookupPort port, HddtProperties p) throws Exception {
        storage.objects.keySet().removeIf(k -> k.startsWith("hddt/export/"));
        JobContext c = hd8Ctx();
        Completion done = new PurchaseVerificationHandler(storage, port, statuses, p, urlPolicy, policy).execute(c);
        String prefix = "hddt/export/hd8-result/" + c.jobId() + "/";
        List<String> lines = new ArrayList<>();
        storage.objects.entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
                .forEach(e -> lines.addAll(java.util.Arrays.asList(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8)
                        .split("\n"))));
        java.util.Collections.sort(lines);
        return done.description() + "\n" + String.join("\n", lines);
    }

    @Test
    void hd8_candidateIndex_sameVerdictsAsPerBatchLookup_ambiguousAndKhmshdonVariants() throws Exception {
        when(statuses.configured()).thenReturn(true);
        when(statuses.findStatuses(any())).thenReturn(Jdk8Compat.mapOf(Fixtures.SELLER, "00"));
        InvoiceRecord ok = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000.000000");
        InvoiceRecord money = Fixtures.record(Fixtures.SELLER, "C26TAA", 2, "500000.000000");
        InvoiceRecord inv = Fixtures.record(Fixtures.SELLER, "C26TAA", 3, "100.000000");
        InvoiceRecord mtt = new InvoiceRecord(inv.nbmst(), inv.khmshdon(), inv.khhdon(), inv.shdon(), inv.tdlap(), inv.nky(),
                inv.tthai(), inv.nbten(), inv.tgtcthue(), inv.tgtthue(), null, null, null, null, "MTT", inv.id());
        InvoiceRecord form1 = Fixtures.record(Fixtures.SELLER, "C26TAA", 4, "700.000000");
        InvoiceRecord form5 = new InvoiceRecord(form1.nbmst(), 5, form1.khhdon(), form1.shdon(), form1.tdlap(), form1.nky(),
                form1.tthai(), form1.nbten(), new BigDecimal("1.000000"), form1.tgtthue(), null, null, null, null, "INV", "id-4b");
        InvoiceRecord outside = new InvoiceRecord(Fixtures.SELLER, 1, "C26TAA", 6L, LocalDate.of(2026, 10, 2), null, 1,
                "X", new BigDecimal("1.000000"), new BigDecimal("0.000000"), null, null, null, null, "INV", "id-6");
        FakeDoris db = new FakeDoris(Jdk8Compat.listOf(ok, money, inv, mtt, form1, form5, outside));
        InvoiceRecord moneyInput = Fixtures.record(Fixtures.SELLER, "C26TAA", 2, "500001.000000");
        InvoiceRecord missing = Fixtures.record(Fixtures.SELLER, "C26TAA", 5, "1.000000");
        InvoiceRecord outsideInput = new InvoiceRecord(Fixtures.SELLER, 1, "C26TAA", 6L, LocalDate.of(2026, 9, 20), null, 1,
                "X", new BigDecimal("1.000000"), new BigDecimal("0.000000"), null, null, null, null, null, null);
        storage.put("hddt", "import/tthc/in_0.csv", input(ok, moneyInput, inv, form1, missing, outsideInput));
        String byIndex = hd8Run(db, props(true));
        int indexQueries = indexCalls.size();
        String perBatch = hd8Run(db, TestProps.withHd8CandidateIndex(props(true), false));
        assertThat(byIndex).isEqualTo(perBatch);
        assertThat(indexQueries).isEqualTo(1);
        assertThat(indexCalls).hasSize(1); // đường cũ không dựng chỉ mục
        // ok + form1 (mẫu 5 cùng số không làm mơ hồ) hợp lệ; tiền lệch, INV/MTT cùng khóa, không có, ngoài khoảng ⇒ 4 dòng
        assertThat(byIndex).contains("kiểm tra 6 hóa đơn, lệch 4").contains("không tồn tại 2, sai lệch 1, nhiều bản ghi 1");
    }

    @Test
    void hd8_tooManyCandidatesPerInputRow_fallsBackToPerBatchLookup_sameResult() throws Exception {
        when(statuses.configured()).thenReturn(true);
        when(statuses.findStatuses(any())).thenReturn(Jdk8Compat.mapOf(Fixtures.SELLER, "00"));
        storage.put("hddt", "import/tthc/in_0.csv", input(Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000.000000")));
        // 2 ứng viên trong khoảng, file 1 dòng, ngưỡng 1 ⇒ 2 > 1 × 1 ⇒ không dựng chỉ mục
        String fallback = hd8Run(doris, TestProps.withHd8CandidateIndex(props(true), true, 1));
        assertThat(indexCalls).isEmpty();
        String indexed = hd8Run(doris, TestProps.withHd8CandidateIndex(props(true), true, 2));
        assertThat(indexCalls).hasSize(1);
        assertThat(indexed).isEqualTo(fallback).contains("kiểm tra 1 hóa đơn, lệch 0");
    }

    @Test
    void hd8_missingFile_andUnconfirmedRules_fail() {
        when(statuses.configured()).thenReturn(true);
        assertThatThrownBy(() -> new PurchaseVerificationHandler(storage, doris, statuses, props(true), urlPolicy, policy)
                .execute(hd8Ctx())).hasMessageContaining("Không tìm thấy file");
        assertThatThrownBy(() -> new PurchaseVerificationHandler(storage, doris, statuses, props(false), urlPolicy, policy)
                .execute(hd8Ctx())).hasMessageContaining("SI-13");
    }

    /** Sổ xử lý TMS trong bộ nhớ. */
    static final class MemRefundStore implements RefundStorePort {
        final Map<UUID, List<Item>> items = new HashMap<>();
        final Map<UUID, Stage> stages = new HashMap<>();
        final Map<UUID, String> sha = new HashMap<>();
        final Map<String, UUID> reserved = new HashMap<>();
        BatchCounters last;

        @Override
        public void startBatch(UUID jobId, int attemptNo, String sourceUrl) {
            stages.put(jobId, Stage.RECEIVED);
            items.remove(jobId);
        }

        @Override
        public void saveItems(UUID jobId, int attemptNo, List<Item> list) {
            items.computeIfAbsent(jobId, k -> new ArrayList<>()).addAll(list);
        }

        @Override
        public void finishValidation(UUID jobId, String contentSha256, int objectCount, long totalBytes,
                                     BatchCounters counters, Stage stage) {
            sha.put(jobId, contentSha256);
            stages.put(jobId, stage);
            last = counters;
        }

        @Override
        public Optional<UUID> reserveContent(UUID jobId, String contentSha256) {
            UUID owner = reserved.putIfAbsent(contentSha256, jobId);
            return owner == null || owner.equals(jobId) ? Optional.empty() : Optional.of(owner);
        }

        @Override
        public void forEachValidItem(UUID jobId, int attemptNo, long rows, int chunkSize, Consumer<List<Item>> chunk) {
            List<Item> valid = items.getOrDefault(jobId, Jdk8Compat.listOf()).stream()
                    .filter(i -> i.status() == ItemStatus.VALID).sorted(Comparator.comparingLong(Item::lineNo)).collect(Jdk8Compat.toList());
            for (int i = 0; i < valid.size(); i += chunkSize) {
                chunk.accept(valid.subList(i, Math.min(valid.size(), i + chunkSize)));
            }
        }

        @Override
        public void updateStage(UUID jobId, Stage stage) {
            stages.put(jobId, stage);
        }
    }

    /** Sổ hoàn trong bộ nhớ: khóa (hóa đơn, MCV) như UNIQUE KEY của HD_SO_HOAN; ghi lại cùng khối không nhân bản. */
    static final class MemLedger implements RefundLedgerPort {
        final Map<String, Entry> rows = new LinkedHashMap<>();
        final List<String> chunks = new ArrayList<>();
        long countDelta;

        @Override
        public Map<InvoiceKey, String> findActiveRefunds(Collection<DatedKey> keys, String excludeMcv) {
            Map<InvoiceKey, String> out = new HashMap<>();
            for (Entry e : rows.values()) {
                InvoiceKey k = new InvoiceKey(e.nbmst(), e.khmshdon(), e.khhdon(), e.shdon());
                if (!e.mcv().equals(excludeMcv) && keys.stream().anyMatch(d -> d.key().equals(k) && d.tdlap().equals(e.tdlap()))) {
                    out.put(k, e.mcv());
                }
            }
            return out;
        }

        @Override
        public void write(String mcv, int chunkNo, List<Entry> entries) {
            chunks.add(mcv + "#" + chunkNo);
            entries.forEach(e -> rows.put(e.nbmst() + "|" + e.khhdon() + "|" + e.shdon() + "|" + e.tdlap() + "|" + mcv, e));
        }

        @Override
        public long countActive(String mcv, LocalDate minTdlap, LocalDate maxTdlap) {
            return rows.values().stream().filter(e -> e.mcv().equals(mcv) && !e.tdlap().isBefore(minTdlap)
                    && !e.tdlap().isAfter(maxTdlap)).count() + countDelta;
        }
    }

    static String tmsLine(long shdon) {
        return "\"0100000002\",1,\"C26TAA\"," + shdon + ",2026-09-15,,1,\"CTY\",940000,75200,"
                + "\"[{\"\"tsuat\"\":\"\"8%\"\",\"\"thtien\"\":940000,\"\"tthue\"\":75200}]\",\"00\",\"Điều chỉnh\"\n";
    }

    JobContext tmsCtx() {
        return HandlerTestSupport.claimed(repo, JobType.REFUND_UPDATE, new RefundParams("s3://hddt/import/tms/ds.csv"));
    }

    HddtProperties tmsProps(HddtProperties.Tms.ApplyMode mode) {
        return TestProps.props("SELECT 1", false, mode, TestProps.fullMapping());
    }

    @Test
    void tms_validList_isWrittenToLedgerInChunks_andReconciled() throws Exception {
        MemRefundStore store = new MemRefundStore();
        MemLedger ledger = new MemLedger();
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1) + tmsLine(2));
        JobContext c = tmsCtx();
        Completion done = new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.LEDGER), urlPolicy).execute(c);
        assertThat(done.rowCount()).isEqualTo(2);
        assertThat(done.description()).contains("Đã ghi sổ hoàn 2 hóa đơn").contains(c.jobId().toString());
        assertThat(store.stages.get(c.jobId())).isEqualTo(RefundStorePort.Stage.APPLIED);
        assertThat(ledger.rows.values()).hasSize(2).allSatisfy(e -> {
            assertThat(e.mcv()).isEqualTo(c.jobId().toString());
            assertThat(e.msthso()).isEqualTo(Fixtures.BUYER);            // NNT được hoàn suy từ NMMST
            assertThat(e.thttltsuat()).isEqualTo("[{\"tsuat\":\"8%\",\"thtien\":940000,\"tthue\":75200}]");
            assertThat(e.tgtthue()).isEqualByComparingTo("75200");
            assertThat(e.loaiHd()).isEqualTo("INV");
        });
        assertThat(ledger.chunks).containsExactly(c.jobId() + "#0"); // ledger-chunk-size = 2
        assertThat(store.reserved).containsValue(c.jobId());
    }

    @Test
    void tms_invoiceAlreadyRefundedUnderAnotherJob_rejectsWholeList() {
        MemRefundStore store = new MemRefundStore();
        MemLedger ledger = new MemLedger();
        ledger.write("11111111-1111-1111-1111-111111111111", 0, Jdk8Compat.listOf(new RefundLedgerPort.Entry(LocalDate.of(2026, 9, 15),
                Fixtures.SELLER, 1, "C26TAA", 2, "11111111-1111-1111-1111-111111111111", "INV", "0100", "id-2",
                Fixtures.BUYER, null, null, null, null, 1, null, 1, "s3://x", "sha", "tms-hthue")));
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1) + tmsLine(2));
        JobContext c = tmsCtx();
        assertThatThrownBy(() -> new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.LEDGER), urlPolicy).execute(c))
                .isInstanceOf(PermanentJobException.class).hasMessageContaining("đã hoàn ở lần cập nhật khác 1")
                .satisfies(e -> assertThat(((PermanentJobException) e).errorCode()).isEqualTo("HDDT-409-DA-HOAN"));
        assertThat(store.last.alreadyRefunded()).isEqualTo(1);
        assertThat(ledger.rows).hasSize(1); // không ghi thêm dòng nào
        assertThat(store.items.get(c.jobId())).anySatisfy(i -> assertThat(i.status())
                .isEqualTo(RefundStorePort.ItemStatus.ALREADY_REFUNDED));
    }

    @Test
    void tms_sameContentReservedByAnotherJob_isDuplicate() {
        MemRefundStore store = new MemRefundStore();
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1));
        JobContext first = tmsCtx();
        JobContext second = tmsCtx();
        try {
            new RefundUpdateHandler(storage, doris, store, new MemLedger(), tmsProps(HddtProperties.Tms.ApplyMode.LEDGER),
                    urlPolicy).execute(first);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        // Ledger khác (rỗng) để chỉ kiểm cơ chế giữ chỗ nội dung
        assertThatThrownBy(() -> new RefundUpdateHandler(storage, doris, store, new MemLedger(),
                tmsProps(HddtProperties.Tms.ApplyMode.LEDGER), urlPolicy).execute(second))
                .hasMessageContaining("đã được ghi nhận ở job " + first.jobId());
    }

    @Test
    void tms_ledgerCountMismatch_isNotReportedAsSuccess() {
        MemRefundStore store = new MemRefundStore();
        MemLedger ledger = new MemLedger();
        ledger.countDelta = -1;
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1));
        assertThatThrownBy(() -> new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.LEDGER), urlPolicy).execute(tmsCtx()))
                .hasMessageContaining("Đối chiếu sổ hoàn không khớp");
    }

    @Test
    void tms_noPartialSuccess_whenAnyRowMissingInvalidOrDuplicated() {
        MemRefundStore store = new MemRefundStore();
        MemLedger ledger = new MemLedger();
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1) + tmsLine(1) + tmsLine(9) + "\"x\",1\n");
        JobContext c = tmsCtx();
        assertThatThrownBy(() -> new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.LEDGER), urlPolicy).execute(c))
                .hasMessageContaining("3/4 dòng không được chấp nhận").hasMessageContaining("không ghi nhận dòng nào");
        assertThat(store.stages.get(c.jobId())).isEqualTo(RefundStorePort.Stage.REJECTED);
        assertThat(store.last.valid()).isEqualTo(1);
        assertThat(store.last.notFound()).isEqualTo(1);
        assertThat(store.last.duplicate()).isEqualTo(1);
        assertThat(store.last.invalid()).isEqualTo(1);
        assertThat(ledger.rows).isEmpty();
    }

    @Test
    void tms_blockedAndValidateOnlyModes_doNotTouchLedger() throws Exception {
        MemRefundStore store = new MemRefundStore();
        MemLedger ledger = new MemLedger();
        storage.put("hddt", "import/tms/ds.csv", tmsLine(1));
        assertThatThrownBy(() -> new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.BLOCKED), urlPolicy).execute(tmsCtx()))
                .hasMessageContaining("ghi sổ hoàn đang tắt");
        Completion done = new RefundUpdateHandler(storage, doris, store, ledger,
                tmsProps(HddtProperties.Tms.ApplyMode.VALIDATE_ONLY), urlPolicy).execute(tmsCtx());
        assertThat(done.description()).contains("CHƯA ghi sổ hoàn thuế");
        assertThat(ledger.rows).isEmpty();
    }
}
