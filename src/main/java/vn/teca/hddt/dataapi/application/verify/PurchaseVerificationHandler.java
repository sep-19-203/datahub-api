package vn.teca.hddt.dataapi.application.verify;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.csv.CsvStreams;
import vn.teca.hddt.dataapi.application.csv.InvoiceRecordCsv;
import vn.teca.hddt.dataapi.application.csv.InvoiceRecordParser;
import vn.teca.hddt.dataapi.application.csv.RowError;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobHandler;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.application.job.JobPayloads.VerifyParams;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.MultiFileWriter;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.application.port.PurchaseCandidates;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.application.storage.InputObjects;
import vn.teca.hddt.dataapi.application.storage.S3Location;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.verify.ComparisonPolicy;
import vn.teca.hddt.dataapi.domain.verify.Outcome;
import vn.teca.hddt.dataapi.domain.verify.ReconciliationEngine;
import vn.teca.hddt.dataapi.domain.verify.Verdict;

/**
 * HD8 — kiểm tra danh sách HĐ mua vào từ file CSV trên S3.
 * Lượt 1: đọc luồng, kiểm tra toàn bộ file (cấu trúc, kiểu, tdlap ∈ [tngay, dngay], trùng khóa). Có lỗi ⇒ job LOI,
 * KHÔNG kết luận đối soát. Lượt 2: đối soát theo lô với Doris (không N+1). DB-P1-02 (TD-API-35, mặc định): MỘT truy vấn
 * ứng viên HD_MST_LIST cho cả job (chỉ giữ khóa có trong file — khóa tra cứu gom ở lượt 1) rồi mỗi lô tra theo khóa vật lý
 * literal; {@code hddt.hd8.candidate-index=false} ⇒ mỗi lô một truy vấn LIST ⨝ HDR như cũ. File kết quả chỉ gồm các dòng
 * không hợp lệ (không tồn tại / sai khác theo {@code hddt.hd8.compare-fields} / nhiều bản ghi). Từ API v3 (SI-65, 08/11):
 * mỗi dòng ghi song song giá trị hệ thống (Doris) và giá trị NNT gửi ({@code *_nnt}) theo {@code CsvLayouts.HD8_RESULT};
 * HĐ không tồn tại / nhiều bản ghi: khóa + tdlap lấy từ file, các cột hệ thống khác để trống.
 * Chỉ trả kết quả khi số dòng ghi = số HĐ lệch và tổng phân loại = số dòng đầu vào.
 */
@Component
public class PurchaseVerificationHandler implements JobHandler {
    private static final Logger log = LoggerFactory.getLogger(PurchaseVerificationHandler.class);

    private final ObjectStoragePort storage;
    private final InvoiceLookupPort lookup;
    private final TaxpayerStatusPort statuses;
    private final HddtProperties props;
    private final S3UrlPolicy urlPolicy;
    private final ReconciliationEngine engine;

    public PurchaseVerificationHandler(ObjectStoragePort storage, InvoiceLookupPort lookup, TaxpayerStatusPort statuses,
                                       HddtProperties props, S3UrlPolicy urlPolicy, ComparisonPolicy comparisonPolicy) {
        this.storage = storage;
        this.lookup = lookup;
        this.statuses = statuses;
        this.props = props;
        this.urlPolicy = urlPolicy;
        this.engine = new ReconciliationEngine(comparisonPolicy);
    }

    @Override
    public Set<JobType> types() {
        return Jdk8Compat.setOf(JobType.PURCHASE_VERIFY);
    }

    @Override
    public Completion execute(JobContext ctx) throws IOException {
        HddtProperties.Hd8 cfg = props.hd8();
        if (!cfg.rulesConfirmed()) {
            throw new PermanentJobException("HDDT-501-RULE", "Quy tắc đối soát HD8 chưa được phê duyệt (SI-13)");
        }
        if (!statuses.configured()) {
            throw new PermanentJobException("HDDT-503-MST-STATUS", "Nguồn trạng thái MST chưa được cấu hình (SI-24)");
        }
        VerifyParams p = JobPayloads.fromJson(ctx.job().payloadJson(), VerifyParams.class);
        LocalDate tngay = DateFormats.parseDate("tngay", p.tngay());
        LocalDate dngay = DateFormats.parseDate("dngay", p.dngay());
        S3Location loc = urlPolicy.parse(p.s3url(), props.s3().hd8InputPrefixes());
        List<StoredObject> inputs = InputObjects.resolve(storage, loc, cfg.maxObjects(), cfg.maxTotalBytes());

        LongHashSet lookupKeys = cfg.candidateIndex() ? new LongHashSet() : null;
        long rows = validate(ctx, inputs, tngay, dngay, lookupKeys);
        PurchaseCandidates index = null;
        if (lookupKeys != null) {
            long candidates = lookup.countPurchaseCandidates(p.nmmst(), tngay, dngay);
            if (useIndex(candidates, rows, cfg.indexMaxCandidatesPerKey())) {
                ctx.progress("{\"phase\":\"INDEXING\",\"rows\":" + rows + "}");
                index = lookup.indexPurchases(p.nmmst(), tngay, dngay, lookupKeys::contains);
                log.info("Job {}: chỉ mục ứng viên HD8 {}/{} dòng LIST cho {} khóa", ctx.jobId(), index.size(), candidates, rows);
            } else {
                log.info("Job {}: {} ứng viên > {} dòng × {} ⇒ tra theo lô (cách cũ)", ctx.jobId(), candidates, rows,
                        cfg.indexMaxCandidatesPerKey());
            }
        }
        return reconcile(ctx, inputs, p.nmmst(), tngay, dngay, rows, index);
    }

    /**
     * Lượt 1 — trả số dòng hợp lệ; ném lỗi nếu có bất kỳ dòng lỗi nào.
     * @param lookupKeys khác null ⇒ gom {@link InvoiceKey#lookupFingerprint} của mọi dòng (DB-P1-02)
     */
    long validate(JobContext ctx, List<StoredObject> inputs, LocalDate tngay, LocalDate dngay, LongHashSet lookupKeys)
            throws IOException {
        HddtProperties.Hd8 cfg = props.hd8();
        List<RowError> sample = new ArrayList<>();
        long errorCount = 0;
        long rows = 0;
        Set<Long> seen = new HashSet<>();
        for (StoredObject o : inputs) {
            try (InputStream in = storage.open(o.bucket(), o.key()); CSVParser parser = CsvStreams.open(in)) {
                for (CSVRecord rec : parser) {
                    if (++rows > cfg.maxRows()) {
                        throw new PermanentJobException("HDDT-413", "File vượt " + cfg.maxRows() + " dòng");
                    }
                    InvoiceRecordParser.Parsed parsed = InvoiceRecordParser.parse(o.key(), rec);
                    List<RowError> errs = new ArrayList<>(parsed.errors());
                    if (parsed.ok()) {
                        InvoiceRecord r = parsed.record();
                        if (r.tdlap().isBefore(tngay) || r.tdlap().isAfter(dngay)) {
                            errs.add(new RowError(o.key(), rec.getRecordNumber(), "tdlap", "ngoài khoảng [tngay, dngay] (SI-33)"));
                        }
                        if (lookupKeys != null) {
                            lookupKeys.add(r.key().lookupFingerprint());
                        }
                        if (!seen.add(fingerprint(r.key()))) {
                            errs.add(new RowError(o.key(), rec.getRecordNumber(), null, "trùng khóa hóa đơn trong file"));
                        }
                    }
                    errorCount += errs.size();
                    for (RowError e : errs) {
                        if (sample.size() < cfg.maxReportedErrors()) {
                            sample.add(e);
                        }
                    }
                    if (rows % 100_000 == 0) {
                        ctx.progress("{\"phase\":\"VALIDATING\",\"rows\":" + rows + "}");
                    }
                }
            } catch (java.nio.charset.CharacterCodingException | java.io.UncheckedIOException e) {
                throw new PermanentJobException("HDDT-400-FILE", "File " + o.key() + " không phải CSV UTF-8 hợp lệ", e);
            }
        }
        if (errorCount > 0) {
            String first = sample.stream().limit(5).map(RowError::describe).collect(Collectors.joining("; "));
            log.warn("Job {}: file đầu vào có {} lỗi", ctx.jobId(), errorCount);
            throw new PermanentJobException("HDDT-400-FILE", "File đầu vào có " + errorCount
                    + " lỗi, không thực hiện đối soát. " + first);
        }
        if (rows == 0) {
            throw new PermanentJobException("HDDT-400-FILE", "File đầu vào không có dòng nào");
        }
        return rows;
    }

    private Completion reconcile(JobContext ctx, List<StoredObject> inputs, String nmmst, LocalDate tngay,
                                 LocalDate dngay, long expectedRows, PurchaseCandidates index) throws IOException {
        String bucket = props.s3().exportBucket();
        String prefix = props.s3().verifyResultPrefix() + "/" + ctx.jobId() + "/";
        List<StoredObject> leftovers = storage.list(bucket, prefix, 10_000);
        if (!leftovers.isEmpty()) {
            storage.delete(bucket, leftovers.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
        }
        String runId = UUID.randomUUID().toString().replace("-", "");
        Map<Outcome, Long> counts = new LinkedHashMap<>();
        for (Outcome oc : Outcome.values()) {
            counts.put(oc, 0L);
        }
        MultiFileWriter writer = storage.openWriter(bucket, prefix, runId, props.export().maxFileBytes());
        List<StoredObject> files;
        try {
            List<InputRow> batch = new ArrayList<>(props.hd8().batchSize());
            for (StoredObject o : inputs) {
                try (InputStream in = storage.open(o.bucket(), o.key()); CSVParser parser = CsvStreams.open(in)) {
                    for (CSVRecord rec : parser) {
                        batch.add(new InputRow(InvoiceRecordParser.parse(o.key(), rec).record(), rec.toList()));
                        if (batch.size() >= props.hd8().batchSize()) {
                            processBatch(nmmst, tngay, dngay, batch, writer, counts, index);
                            batch.clear();
                            ctx.progress("{\"phase\":\"RECONCILING\",\"rows\":" + total(counts) + "}");
                        }
                    }
                }
            }
            if (!batch.isEmpty()) {
                processBatch(nmmst, tngay, dngay, batch, writer, counts, index);
            }
            ctx.ensureOwned();
            files = writer.finish();
        } catch (RuntimeException | IOException e) {
            writer.abort();
            throw e;
        }
        long discrepancies = counts.get(Outcome.NOT_FOUND) + counts.get(Outcome.MISMATCH) + counts.get(Outcome.AMBIGUOUS);
        if (total(counts) != expectedRows || writer.lines() != discrepancies) {
            throw new PermanentJobException("HDDT-500-VERIFY", "Đối chiếu số dòng không khớp (đầu vào " + expectedRows
                    + ", đã phân loại " + total(counts) + ", ghi " + writer.lines() + ")");
        }
        String url = "s3://" + bucket + "/" + prefix + runId + "_*";
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("jobId", ctx.jobId().toString());
        manifest.put("inputs", inputs.stream().map(o -> Jdk8Compat.mapOf("key", o.key(), "size", o.size())).collect(Jdk8Compat.toList()));
        manifest.put("counts", counts);
        manifest.put("url", url);
        manifest.put("files", files.stream().map(f -> Jdk8Compat.mapOf("key", f.key(), "size", f.size())).collect(Jdk8Compat.toList()));
        String mta = "Thành công: kiểm tra " + expectedRows + " hóa đơn, lệch " + discrepancies + " (không tồn tại "
                + counts.get(Outcome.NOT_FOUND) + ", sai lệch " + counts.get(Outcome.MISMATCH) + ", nhiều bản ghi "
                + counts.get(Outcome.AMBIGUOUS) + ")";
        return new Completion(bucket, prefix, url, JobPayloads.toJson(manifest), discrepancies, mta);
    }

    /** Dòng đầu vào đã kiểm tra + giá trị nguyên văn (để ghi lại văn bản NNT gửi vào các cột {@code *_nnt}). */
    static final class InputRow {
        private final InvoiceRecord record;
        private final List<String> raw;

        InputRow(InvoiceRecord record, List<String> raw) {
            this.record = record;
            this.raw = raw;
        }

        public InvoiceRecord record() {
            return record;
        }

        public List<String> raw() {
            return raw;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InputRow)) {
                return false;
            }
            InputRow other = (InputRow) o;
            return Objects.equals(record, other.record)
                    && Objects.equals(raw, other.raw);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(record);
            result = 31 * result + Objects.hashCode(raw);
            return result;
        }

        @Override
        public String toString() {
            return "InputRow[record=" + record
                    + ", raw=" + raw + "]";
        }
    }

    /** @param index chỉ mục ứng viên của job (DB-P1-02); null ⇒ cách cũ, mỗi lô một truy vấn LIST ⨝ HDR */
    void processBatch(String nmmst, LocalDate tngay, LocalDate dngay, List<InputRow> batch, MultiFileWriter writer,
                      Map<Outcome, Long> counts, PurchaseCandidates index) throws IOException {
        Set<InvoiceKey> keys = new HashSet<>();
        batch.forEach(r -> keys.add(r.record().key()));
        List<InvoiceRecord> found = index == null ? lookup.findPurchases(nmmst, tngay, dngay, keys)
                : lookup.fetchPurchases(nmmst, index, keys);
        // ttmstnban (Required Y) cho mọi dòng kết quả, kể cả HĐ không tồn tại ⇒ tra theo nbmst của cả lô (1 lần gọi/lô như cũ).
        Set<String> sellers = new HashSet<>();
        found.forEach(r -> sellers.add(r.nbmst()));
        batch.forEach(r -> sellers.add(r.record().nbmst()));
        Map<String, String> sellerStatus = statuses.findStatuses(sellers);
        Map<InvoiceKey, List<InvoiceRecord>> byKey = new HashMap<>();
        for (InvoiceRecord r : found) {
            byKey.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r.withTtmstnban(sellerStatus.get(r.nbmst())));
        }
        for (InputRow input : batch) {
            Verdict v = engine.verify(input.record(), byKey.get(input.record().key()));
            counts.merge(v.outcome(), 1L, Long::sum);
            if (v.discrepancy()) {
                InvoiceRecord sys = v.actual();
                String nbmst = sys != null ? sys.nbmst() : input.record().nbmst();
                writer.writeLine(InvoiceRecordCsv.resultLine(input.record(), input.raw(), sys, sellerStatus.get(nbmst)));
            }
        }
    }

    /** Dựng chỉ mục khi số ứng viên phải đọc ≤ số dòng file × ngưỡng (DB-P1-02). */
    static boolean useIndex(long candidates, long rows, int maxCandidatesPerKey) {
        return candidates <= rows * (long) Math.max(1, maxCandidatesPerKey);
    }

    private static long total(Map<Outcome, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Dấu vân tay 64-bit của khóa để phát hiện trùng với bộ nhớ thấp (xác suất va chạm ~1e-8 ở 1 triệu dòng). */
    static long fingerprint(InvoiceKey k) {
        long h = 1125899906842597L;
        String s = k.nbmst() + '\u0001' + k.khmshdon() + '\u0001' + k.khhdon() + '\u0001' + k.shdon();
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
            h ^= (h >>> 29);
        }
        return h;
    }
}
