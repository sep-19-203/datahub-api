package vn.teca.hddt.dataapi.infrastructure.dse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.springframework.stereotype.Repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import vn.teca.hddt.dataapi.application.port.RefundStorePort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Sổ xử lý TMS trên DSE: refund_batch, refund_item (theo lần thử, bucket 10.000 dòng — đồng thời là staging cho ghi sổ
 * hoàn), refund_applied_content (giữ chỗ nội dung bằng LWT).
 */
@Repository
public class DseRefundStore implements RefundStorePort {
    static final int BUCKET = 10_000;
    private static final int IN_FLIGHT = 128;

    private final CqlSession s;
    private final PreparedStatement upsertBatch;
    private final PreparedStatement insertItem;
    private final PreparedStatement selectItems;
    private final PreparedStatement finish;
    private final PreparedStatement stage;
    private final PreparedStatement reserve;

    public DseRefundStore(CqlSession jobStoreSession, HddtProperties props) {
        this.s = jobStoreSession;
        String k = props.dse().keyspace() + ".";
        upsertBatch = s.prepare("UPDATE " + k + "refund_batch SET source_url = ?, items_attempt = ?, stage = 'RECEIVED',"
                + " created_at = ?, updated_at = ? WHERE job_id = ?");
        insertItem = s.prepare("INSERT INTO " + k + "refund_item (job_id, attempt_no, bucket, line_no, nbmst, khmshdon,"
                + " khhdon, shdon, tdlap, item_status, detail, doris_loai_hd, doris_id, doris_cqt, nmmst, tgtcthue, tgtthue,"
                + " thttltsuat, ttmstnban, tthai, nky) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        selectItems = s.prepare("SELECT * FROM " + k + "refund_item WHERE job_id = ? AND attempt_no = ? AND bucket = ?");
        finish = s.prepare("UPDATE " + k + "refund_batch SET content_sha256 = ?, object_count = ?, total_bytes = ?,"
                + " row_count = ?, valid_count = ?, invalid_count = ?, not_found_count = ?, ambiguous_count = ?,"
                + " duplicate_count = ?, already_refunded_count = ?, stage = ?, updated_at = ? WHERE job_id = ?");
        stage = s.prepare("UPDATE " + k + "refund_batch SET stage = ?, updated_at = ? WHERE job_id = ?");
        reserve = s.prepare("INSERT INTO " + k + "refund_applied_content (content_sha256, job_id, applied_at)"
                + " VALUES (?, ?, ?) IF NOT EXISTS");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Override
    public void startBatch(UUID jobId, int attemptNo, String sourceUrl) {
        Instant now = now();
        s.execute(upsertBatch.bind(sourceUrl, attemptNo, now, now, jobId));
    }

    @Override
    public void saveItems(UUID jobId, int attemptNo, List<Item> items) {
        List<CompletionStage<AsyncResultSet>> pending = new ArrayList<>(IN_FLIGHT);
        for (Item i : items) {
            pending.add(s.executeAsync(insertItem.bind(jobId, attemptNo, (int) (i.lineNo() / BUCKET), i.lineNo(),
                    i.nbmst(), i.khmshdon(), i.khhdon(), i.shdon(), i.tdlap(), i.status().name(), i.detail(),
                    i.dorisLoaiHd(), i.dorisId(), i.dorisCqt(), i.nmmst(), i.tgtcthue(), i.tgtthue(), i.thttltsuat(),
                    i.ttmstnban(), i.tthai(),
                    i.nky() == null ? null : i.nky().atZone(vn.teca.hddt.dataapi.domain.common.DateFormats.VN).toInstant())));
            if (pending.size() >= IN_FLIGHT) {
                drain(pending);
            }
        }
        drain(pending);
    }

    private static void drain(List<CompletionStage<AsyncResultSet>> pending) {
        for (CompletionStage<AsyncResultSet> f : pending) {
            f.toCompletableFuture().join();
        }
        pending.clear();
    }

    @Override
    public void finishValidation(UUID jobId, String contentSha256, int objectCount, long totalBytes, BatchCounters c,
                                 Stage st) {
        s.execute(finish.bind(contentSha256, objectCount, totalBytes, c.rows(), c.valid(), c.invalid(), c.notFound(),
                c.ambiguous(), c.duplicate(), c.alreadyRefunded(), st.name(), now(), jobId));
    }

    @Override
    public Optional<UUID> reserveContent(UUID jobId, String contentSha256) {
        Row r = s.execute(reserve.bind(contentSha256, jobId, now())).one();
        if (r == null || r.getBoolean("[applied]")) {
            return Optional.empty();
        }
        UUID owner = r.getUuid("job_id");
        return owner == null || owner.equals(jobId) ? Optional.empty() : Optional.of(owner);
    }

    @Override
    public void forEachValidItem(UUID jobId, int attemptNo, long rows, int chunkSize, Consumer<List<Item>> chunk) {
        List<Item> buf = new ArrayList<>(chunkSize);
        long lastBucket = rows <= 0 ? 0 : rows / BUCKET;
        for (int b = 0; b <= lastBucket; b++) {
            for (Row r : s.execute(selectItems.bind(jobId, attemptNo, b))) {
                if (!ItemStatus.VALID.name().equals(r.getString("item_status"))) {
                    continue;
                }
                buf.add(map(r));
                if (buf.size() >= chunkSize) {
                    chunk.accept(Jdk8Compat.listCopyOf(buf));
                    buf.clear();
                }
            }
        }
        if (!buf.isEmpty()) {
            chunk.accept(Jdk8Compat.listCopyOf(buf));
        }
    }

    private static Item map(Row r) {
        Instant nky = r.getInstant("nky");
        return new Item(r.getLong("line_no"), r.getString("nbmst"), r.isNull("khmshdon") ? null : r.getInt("khmshdon"),
                r.getString("khhdon"), r.isNull("shdon") ? null : r.getLong("shdon"), r.getLocalDate("tdlap"),
                ItemStatus.valueOf(r.getString("item_status")), r.getString("detail"), r.getString("doris_loai_hd"),
                r.getString("doris_id"), r.getString("doris_cqt"), r.getString("nmmst"), r.getBigDecimal("tgtcthue"),
                r.getBigDecimal("tgtthue"), r.getString("thttltsuat"), r.getString("ttmstnban"),
                r.isNull("tthai") ? null : r.getInt("tthai"),
                nky == null ? null : java.time.LocalDateTime.ofInstant(nky, vn.teca.hddt.dataapi.domain.common.DateFormats.VN));
    }

    @Override
    public void updateStage(UUID jobId, Stage st) {
        s.execute(stage.bind(st.name(), now(), jobId));
    }
}
