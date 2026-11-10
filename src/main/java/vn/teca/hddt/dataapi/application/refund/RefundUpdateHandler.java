package vn.teca.hddt.dataapi.application.refund;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.csv.CsvStreams;
import vn.teca.hddt.dataapi.application.csv.RefundRowParser;
import vn.teca.hddt.dataapi.application.csv.RowError;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobHandler;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.application.job.JobPayloads.RefundParams;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.RefundTarget;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.application.port.RefundLedgerPort;
import vn.teca.hddt.dataapi.application.port.RefundStorePort;
import vn.teca.hddt.dataapi.application.port.RefundStorePort.BatchCounters;
import vn.teca.hddt.dataapi.application.port.RefundStorePort.Item;
import vn.teca.hddt.dataapi.application.port.RefundStorePort.ItemStatus;
import vn.teca.hddt.dataapi.application.port.RefundStorePort.Stage;
import vn.teca.hddt.dataapi.application.storage.InputObjects;
import vn.teca.hddt.dataapi.application.storage.S3Location;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.config.HddtProperties.Tms.ApplyMode;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.refund.RefundRow;

/**
 * TMS — cập nhật danh sách HĐ đã hoàn thuế. Các giai đoạn tách bạch (sổ xử lý DSE):
 * <ol>
 * <li>RECEIVED: đọc luồng file, kiểm tra từng dòng, đối chiếu Doris theo lô (1 truy vấn/lô), kiểm tra HĐ đã ở trạng thái
 *     DA_HOAN dưới mã công việc khác (chống hoàn trùng); lưu từng dòng (dòng hợp lệ mang đủ dữ liệu dòng sổ — staging).</li>
 * <li>VALIDATED / REJECTED: một dòng không được chấp nhận ⇒ cả lô bị từ chối, không ghi sổ dòng nào (không partial).</li>
 * <li>Giữ chỗ nội dung (LWT theo SHA-256) ⇒ cùng một danh sách không được ghi sổ hai lần.</li>
 * <li>APPLYING → APPLIED: ghi sổ hoàn Doris theo khối có label (idempotent khi thử lại), đối chiếu số dòng DA_HOAN của
 *     mã công việc = số dòng hợp lệ rồi mới hoàn tất.</li>
 * </ol>
 * MCV (mã công việc) = jobId. Không ghi đè HOADON_HDR / HD_MST_SUMMARY. Thử lại sau lỗi: xử lý lại cả lô ở lần thử mới.
 * Hai job TMS chạy song song có cùng hóa đơn có thể cùng vượt qua bước chống hoàn trùng ⇒ giới hạn REFUND_UPDATE chạy 1 job.
 */
@Component
public class RefundUpdateHandler implements JobHandler {
    private static final Logger log = LoggerFactory.getLogger(RefundUpdateHandler.class);

    private final ObjectStoragePort storage;
    private final InvoiceLookupPort lookup;
    private final RefundStorePort store;
    private final RefundLedgerPort ledger;
    private final HddtProperties props;
    private final S3UrlPolicy urlPolicy;

    public RefundUpdateHandler(ObjectStoragePort storage, InvoiceLookupPort lookup, RefundStorePort store,
                               RefundLedgerPort ledger, HddtProperties props, S3UrlPolicy urlPolicy) {
        this.storage = storage;
        this.lookup = lookup;
        this.store = store;
        this.ledger = ledger;
        this.props = props;
        this.urlPolicy = urlPolicy;
    }

    @Override
    public Set<JobType> types() {
        return Jdk8Compat.setOf(JobType.REFUND_UPDATE);
    }

    /** Bộ đếm theo lô + khoảng TDLAP của dòng hợp lệ. */
    static final class Counters {
        long rows;
        long valid;
        long invalid;
        long notFound;
        long ambiguous;
        long duplicate;
        long alreadyRefunded;
        LocalDate minTdlap;
        LocalDate maxTdlap;

        BatchCounters snapshot() {
            return new BatchCounters(rows, valid, invalid, notFound, ambiguous, duplicate, alreadyRefunded);
        }

        void validDate(LocalDate d) {
            minTdlap = minTdlap == null || d.isBefore(minTdlap) ? d : minTdlap;
            maxTdlap = maxTdlap == null || d.isAfter(maxTdlap) ? d : maxTdlap;
        }
    }

    @Override
    public Completion execute(JobContext ctx) throws IOException {
        HddtProperties.Tms cfg = props.tms();
        RefundParams p = JobPayloads.fromJson(ctx.job().payloadJson(), RefundParams.class);
        S3Location loc = urlPolicy.parse(p.s3url(), props.s3().tmsInputPrefixes());
        List<StoredObject> inputs = InputObjects.resolve(storage, loc, cfg.maxObjects(), cfg.maxTotalBytes());
        UUID jobId = ctx.jobId();
        String mcv = jobId.toString();
        int attempt = ctx.attemptNo();
        store.startBatch(jobId, attempt, loc.toUri());

        MessageDigest sha = sha256();
        Counters c = new Counters();
        Set<InvoiceKey> seen = new HashSet<>();
        List<RefundRow> pending = new ArrayList<>();
        List<Item> rejectedItems = new ArrayList<>();
        RowError firstError = null;
        for (StoredObject o : inputs) {
            try (InputStream raw = storage.open(o.bucket(), o.key());
                 DigestInputStream in = new DigestInputStream(raw, sha);
                 CSVParser parser = CsvStreams.open(in)) {
                for (CSVRecord rec : parser) {
                    if (++c.rows > cfg.maxRows()) {
                        throw new PermanentJobException("HDDT-413", "File vượt " + cfg.maxRows() + " dòng");
                    }
                    long lineNo = c.rows;
                    RefundRowParser.Parsed parsed = RefundRowParser.parse(o.key(), rec);
                    if (!parsed.ok()) {
                        RowError e = parsed.errors().get(0);
                        firstError = firstError == null ? e : firstError;
                        rejectedItems.add(Item.rejected(lineNo, ItemStatus.INVALID, shorten(e.describe())));
                        c.invalid++;
                    } else {
                        RefundRow r = withLineNo(parsed.row(), lineNo);
                        if (!seen.add(r.key())) {
                            rejectedItems.add(item(r, ItemStatus.DUPLICATE, "trùng khóa trong danh sách", null));
                            c.duplicate++;
                        } else {
                            pending.add(r);
                        }
                    }
                    if (pending.size() >= cfg.batchSize()) {
                        matchAndSave(jobId, mcv, attempt, pending, c);
                        pending.clear();
                        ctx.progress("{\"phase\":\"VALIDATING\",\"rows\":" + c.rows + "}");
                    }
                    if (rejectedItems.size() >= cfg.batchSize()) {
                        store.saveItems(jobId, attempt, rejectedItems);
                        rejectedItems.clear();
                    }
                }
                Jdk8Compat.drain(in);
            } catch (java.nio.charset.CharacterCodingException | java.io.UncheckedIOException e) {
                store.updateStage(jobId, Stage.REJECTED);
                throw new PermanentJobException("HDDT-400-FILE", "File " + o.key() + " không phải CSV UTF-8 hợp lệ", e);
            }
        }
        if (!pending.isEmpty()) {
            matchAndSave(jobId, mcv, attempt, pending, c);
        }
        if (!rejectedItems.isEmpty()) {
            store.saveItems(jobId, attempt, rejectedItems);
        }
        String contentSha = Jdk8Compat.formatHex(sha.digest());
        BatchCounters counters = c.snapshot();
        long totalBytes = inputs.stream().mapToLong(StoredObject::size).sum();
        boolean rejected = counters.rejected() > 0 || c.rows == 0;
        store.finishValidation(jobId, contentSha, inputs.size(), totalBytes, counters,
                rejected ? Stage.REJECTED : Stage.VALIDATED);
        ctx.ensureOwned();

        if (c.rows == 0) {
            throw new PermanentJobException("HDDT-400-FILE", "Danh sách không có dòng nào");
        }
        if (rejected) {
            throw new PermanentJobException(c.alreadyRefunded > 0 ? "HDDT-409-DA-HOAN" : "HDDT-400-FILE",
                    "Danh sách có " + counters.rejected() + "/" + c.rows + " dòng không được chấp nhận (lỗi định dạng "
                    + c.invalid + ", không tồn tại " + c.notFound + ", nhiều bản ghi " + c.ambiguous + ", trùng " + c.duplicate
                    + ", đã hoàn ở lần cập nhật khác " + c.alreadyRefunded + "); không ghi nhận dòng nào"
                    + (firstError == null ? "" : ". Lỗi đầu tiên: " + firstError.describe()));
        }
        if (cfg.applyMode() == ApplyMode.VALIDATE_ONLY) {
            log.warn("Job {} chạy VALIDATE_ONLY: không ghi sổ hoàn", jobId);
            return new Completion(null, null, null, manifest(contentSha, counters, "VALIDATE_ONLY"), c.rows,
                    "Đã kiểm tra " + c.rows + " hóa đơn hợp lệ; CHƯA ghi sổ hoàn thuế (chế độ VALIDATE_ONLY)");
        }
        if (cfg.applyMode() == ApplyMode.BLOCKED) {
            store.updateStage(jobId, Stage.APPLY_BLOCKED);
            throw new PermanentJobException("HDDT-501-RULE", "Đã kiểm tra " + c.valid
                    + " hóa đơn hợp lệ nhưng ghi sổ hoàn đang tắt (hddt.tms.apply-mode=BLOCKED); chưa ghi nhận");
        }
        Optional<UUID> owner = store.reserveContent(jobId, contentSha);
        if (owner.isPresent()) {
            throw new PermanentJobException("HDDT-409-DUPLICATE", "Danh sách này đã được ghi nhận ở job " + owner.get());
        }
        store.updateStage(jobId, Stage.APPLYING);
        long written = writeLedger(ctx, mcv, attempt, c.rows, loc.toUri(), contentSha);
        long inLedger = ledger.countActive(mcv, c.minTdlap, c.maxTdlap);
        if (written != c.valid || inLedger != c.valid) {
            throw new PermanentJobException("HDDT-500-LEDGER", "Đối chiếu sổ hoàn không khớp (hợp lệ " + c.valid
                    + ", đã ghi " + written + ", trên sổ " + inLedger + ")");
        }
        store.updateStage(jobId, Stage.APPLIED);
        log.info("Job {} ghi sổ hoàn {} hóa đơn", jobId, inLedger);
        return new Completion(null, null, null, manifest(contentSha, counters, "LEDGER"), inLedger,
                "Đã ghi sổ hoàn " + inLedger + " hóa đơn (mã công việc " + mcv + ")");
    }

    private long writeLedger(JobContext ctx, String mcv, int attempt, long rows, String source, String contentSha) {
        AtomicInteger chunkNo = new AtomicInteger();
        AtomicLong written = new AtomicLong();
        String clientId = ctx.job().clientId();
        store.forEachValidItem(ctx.jobId(), attempt, rows, props.tms().ledgerChunkSize(), items -> {
            List<RefundLedgerPort.Entry> entries = items.stream().map(i -> new RefundLedgerPort.Entry(i.tdlap(),
                    i.nbmst(), i.khmshdon(), i.khhdon(), i.shdon(), mcv, i.dorisLoaiHd(), i.dorisCqt(), i.dorisId(),
                    i.nmmst(), i.tgtcthue(), i.tgtthue(), i.thttltsuat(), i.ttmstnban(), i.tthai(), i.nky(), i.lineNo(),
                    source, contentSha, clientId)).collect(Jdk8Compat.toList());
            ledger.write(mcv, chunkNo.getAndIncrement(), entries);
            written.addAndGet(entries.size());
            ctx.progress("{\"phase\":\"LEDGER\",\"written\":" + written.get() + "}");
        });
        return written.get();
    }

    private void matchAndSave(UUID jobId, String mcv, int attempt, List<RefundRow> rows, Counters c) {
        List<DatedKey> keys = rows.stream().map(r -> new DatedKey(r.key(), r.tdlap())).collect(Jdk8Compat.toList());
        Map<InvoiceKey, List<RefundTarget>> found = new HashMap<>();
        for (RefundTarget t : lookup.findRefundTargets(keys)) {
            found.computeIfAbsent(t.key(), k -> new ArrayList<>()).add(t);
        }
        Map<InvoiceKey, String> refunded = ledger.findActiveRefunds(keys, mcv);
        List<Item> items = new ArrayList<>(rows.size());
        for (RefundRow r : rows) {
            List<RefundTarget> m = found.getOrDefault(r.key(), Jdk8Compat.listOf()).stream()
                    .filter(t -> r.tdlap().equals(t.tdlap())).collect(Jdk8Compat.toList());
            if (m.isEmpty()) {
                items.add(item(r, ItemStatus.NOT_FOUND, "không có trên Doris", null));
                c.notFound++;
            } else if (m.size() > 1) {
                items.add(item(r, ItemStatus.AMBIGUOUS, m.size() + " bản ghi cùng khóa", null));
                c.ambiguous++;
            } else if (refunded.containsKey(r.key())) {
                items.add(item(r, ItemStatus.ALREADY_REFUNDED, "đã hoàn ở mã công việc " + refunded.get(r.key()), null));
                c.alreadyRefunded++;
            } else {
                items.add(item(r, ItemStatus.VALID, null, m.get(0)));
                c.valid++;
                c.validDate(r.tdlap());
            }
        }
        store.saveItems(jobId, attempt, items);
    }

    private static Item item(RefundRow r, ItemStatus status, String detail, RefundTarget match) {
        return new Item(r.lineNo(), r.nbmst(), r.khmshdon(), r.khhdon(), r.shdon(), r.tdlap(), status, detail,
                match == null ? null : match.loaiHd(), match == null ? null : match.id(),
                match == null ? null : match.cqt(), match == null ? null : match.nmmst(), r.tgtcthue(), r.tgtthue(),
                r.thttltsuatJson(), r.ttmstnban(), r.tthai(), r.nky());
    }

    private static RefundRow withLineNo(RefundRow r, long lineNo) {
        return new RefundRow(lineNo, r.nbmst(), r.khmshdon(), r.khhdon(), r.shdon(), r.tdlap(), r.nky(), r.tthai(),
                r.nbten(), r.tgtcthue(), r.tgtthue(), r.thttltsuat(), r.ttmstnban(), r.thttltsuatJson(), r.lddctthe());
    }

    private static String manifest(String contentSha, BatchCounters counters, String mode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contentSha256", contentSha);
        m.put("counters", counters);
        m.put("applyMode", mode);
        return JobPayloads.toJson(m);
    }

    private static String shorten(String s) {
        return s.length() > 900 ? s.substring(0, 900) : s;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
