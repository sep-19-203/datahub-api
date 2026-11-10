package vn.teca.hddt.dataapi.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sổ xử lý TMS theo lô/từng dòng (audit, staging, chống trùng) — DSE refund_batch / refund_item / refund_applied_content.
 * Giai đoạn: RECEIVED → VALIDATED | REJECTED → APPLYING → APPLIED (hoặc APPLY_BLOCKED khi tắt ghi sổ).
 */
public interface RefundStorePort {

    enum Stage { RECEIVED, VALIDATED, REJECTED, APPLY_BLOCKED, APPLYING, APPLIED }

    enum ItemStatus { VALID, INVALID, NOT_FOUND, AMBIGUOUS, DUPLICATE, ALREADY_REFUNDED }

    /** Một dòng TMS + dữ liệu khớp trên Doris (dòng VALID mang đủ dữ liệu để ghi sổ hoàn). */
    static final class Item {
        private final long lineNo;
        private final String nbmst;
        private final Integer khmshdon;
        private final String khhdon;
        private final Long shdon;
        private final LocalDate tdlap;
        private final ItemStatus status;
        private final String detail;
        private final String dorisLoaiHd;
        private final String dorisId;
        private final String dorisCqt;
        private final String nmmst;
        private final BigDecimal tgtcthue;
        private final BigDecimal tgtthue;
        private final String thttltsuat;
        private final String ttmstnban;
        private final Integer tthai;
        private final LocalDateTime nky;

        public Item(long lineNo, String nbmst, Integer khmshdon, String khhdon, Long shdon, LocalDate tdlap, ItemStatus status,
                String detail, String dorisLoaiHd, String dorisId, String dorisCqt, String nmmst, BigDecimal tgtcthue,
                BigDecimal tgtthue, String thttltsuat, String ttmstnban, Integer tthai, LocalDateTime nky) {
            this.lineNo = lineNo;
            this.nbmst = nbmst;
            this.khmshdon = khmshdon;
            this.khhdon = khhdon;
            this.shdon = shdon;
            this.tdlap = tdlap;
            this.status = status;
            this.detail = detail;
            this.dorisLoaiHd = dorisLoaiHd;
            this.dorisId = dorisId;
            this.dorisCqt = dorisCqt;
            this.nmmst = nmmst;
            this.tgtcthue = tgtcthue;
            this.tgtthue = tgtthue;
            this.thttltsuat = thttltsuat;
            this.ttmstnban = ttmstnban;
            this.tthai = tthai;
            this.nky = nky;
        }

        public long lineNo() {
            return lineNo;
        }

        public String nbmst() {
            return nbmst;
        }

        public Integer khmshdon() {
            return khmshdon;
        }

        public String khhdon() {
            return khhdon;
        }

        public Long shdon() {
            return shdon;
        }

        public LocalDate tdlap() {
            return tdlap;
        }

        public ItemStatus status() {
            return status;
        }

        public String detail() {
            return detail;
        }

        public String dorisLoaiHd() {
            return dorisLoaiHd;
        }

        public String dorisId() {
            return dorisId;
        }

        public String dorisCqt() {
            return dorisCqt;
        }

        public String nmmst() {
            return nmmst;
        }

        public BigDecimal tgtcthue() {
            return tgtcthue;
        }

        public BigDecimal tgtthue() {
            return tgtthue;
        }

        public String thttltsuat() {
            return thttltsuat;
        }

        public String ttmstnban() {
            return ttmstnban;
        }

        public Integer tthai() {
            return tthai;
        }

        public LocalDateTime nky() {
            return nky;
        }

        public static Item rejected(long lineNo, ItemStatus status, String detail) {
            return new Item(lineNo, null, null, null, null, null, status, detail, null, null, null, null, null, null,
                    null, null, null, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Item)) {
                return false;
            }
            Item other = (Item) o;
            return lineNo == other.lineNo
                    && Objects.equals(nbmst, other.nbmst)
                    && Objects.equals(khmshdon, other.khmshdon)
                    && Objects.equals(khhdon, other.khhdon)
                    && Objects.equals(shdon, other.shdon)
                    && Objects.equals(tdlap, other.tdlap)
                    && Objects.equals(status, other.status)
                    && Objects.equals(detail, other.detail)
                    && Objects.equals(dorisLoaiHd, other.dorisLoaiHd)
                    && Objects.equals(dorisId, other.dorisId)
                    && Objects.equals(dorisCqt, other.dorisCqt)
                    && Objects.equals(nmmst, other.nmmst)
                    && Objects.equals(tgtcthue, other.tgtcthue)
                    && Objects.equals(tgtthue, other.tgtthue)
                    && Objects.equals(thttltsuat, other.thttltsuat)
                    && Objects.equals(ttmstnban, other.ttmstnban)
                    && Objects.equals(tthai, other.tthai)
                    && Objects.equals(nky, other.nky);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(lineNo);
            result = 31 * result + Objects.hashCode(nbmst);
            result = 31 * result + Objects.hashCode(khmshdon);
            result = 31 * result + Objects.hashCode(khhdon);
            result = 31 * result + Objects.hashCode(shdon);
            result = 31 * result + Objects.hashCode(tdlap);
            result = 31 * result + Objects.hashCode(status);
            result = 31 * result + Objects.hashCode(detail);
            result = 31 * result + Objects.hashCode(dorisLoaiHd);
            result = 31 * result + Objects.hashCode(dorisId);
            result = 31 * result + Objects.hashCode(dorisCqt);
            result = 31 * result + Objects.hashCode(nmmst);
            result = 31 * result + Objects.hashCode(tgtcthue);
            result = 31 * result + Objects.hashCode(tgtthue);
            result = 31 * result + Objects.hashCode(thttltsuat);
            result = 31 * result + Objects.hashCode(ttmstnban);
            result = 31 * result + Objects.hashCode(tthai);
            result = 31 * result + Objects.hashCode(nky);
            return result;
        }

        @Override
        public String toString() {
            return "Item[lineNo=" + lineNo
                    + ", nbmst=" + nbmst
                    + ", khmshdon=" + khmshdon
                    + ", khhdon=" + khhdon
                    + ", shdon=" + shdon
                    + ", tdlap=" + tdlap
                    + ", status=" + status
                    + ", detail=" + detail
                    + ", dorisLoaiHd=" + dorisLoaiHd
                    + ", dorisId=" + dorisId
                    + ", dorisCqt=" + dorisCqt
                    + ", nmmst=" + nmmst
                    + ", tgtcthue=" + tgtcthue
                    + ", tgtthue=" + tgtthue
                    + ", thttltsuat=" + thttltsuat
                    + ", ttmstnban=" + ttmstnban
                    + ", tthai=" + tthai
                    + ", nky=" + nky + "]";
        }
    }

    // Được tuần tự hóa vào manifest TMS (JobPayloads, thuộc tính sắp theo tên) — như các thành phần của record trước đây.
    static final class BatchCounters {
        @JsonProperty("rows")
        private final long rows;
        @JsonProperty("valid")
        private final long valid;
        @JsonProperty("invalid")
        private final long invalid;
        @JsonProperty("notFound")
        private final long notFound;
        @JsonProperty("ambiguous")
        private final long ambiguous;
        @JsonProperty("duplicate")
        private final long duplicate;
        @JsonProperty("alreadyRefunded")
        private final long alreadyRefunded;

        public BatchCounters(long rows, long valid, long invalid, long notFound, long ambiguous, long duplicate,
                long alreadyRefunded) {
            this.rows = rows;
            this.valid = valid;
            this.invalid = invalid;
            this.notFound = notFound;
            this.ambiguous = ambiguous;
            this.duplicate = duplicate;
            this.alreadyRefunded = alreadyRefunded;
        }

        public long rows() {
            return rows;
        }

        public long valid() {
            return valid;
        }

        public long invalid() {
            return invalid;
        }

        public long notFound() {
            return notFound;
        }

        public long ambiguous() {
            return ambiguous;
        }

        public long duplicate() {
            return duplicate;
        }

        public long alreadyRefunded() {
            return alreadyRefunded;
        }

        public long rejected() {
            return invalid + notFound + ambiguous + duplicate + alreadyRefunded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BatchCounters)) {
                return false;
            }
            BatchCounters other = (BatchCounters) o;
            return rows == other.rows
                    && valid == other.valid
                    && invalid == other.invalid
                    && notFound == other.notFound
                    && ambiguous == other.ambiguous
                    && duplicate == other.duplicate
                    && alreadyRefunded == other.alreadyRefunded;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(rows);
            result = 31 * result + Long.hashCode(valid);
            result = 31 * result + Long.hashCode(invalid);
            result = 31 * result + Long.hashCode(notFound);
            result = 31 * result + Long.hashCode(ambiguous);
            result = 31 * result + Long.hashCode(duplicate);
            result = 31 * result + Long.hashCode(alreadyRefunded);
            return result;
        }

        @Override
        public String toString() {
            return "BatchCounters[rows=" + rows
                    + ", valid=" + valid
                    + ", invalid=" + invalid
                    + ", notFound=" + notFound
                    + ", ambiguous=" + ambiguous
                    + ", duplicate=" + duplicate
                    + ", alreadyRefunded=" + alreadyRefunded + "]";
        }
    }

    /** Bắt đầu (lại) lô ở lần thử {@code attemptNo}; dòng của lần thử trước giữ nguyên làm lịch sử, không bị dùng lại. */
    void startBatch(UUID jobId, int attemptNo, String sourceUrl);

    void saveItems(UUID jobId, int attemptNo, List<Item> items);

    void finishValidation(UUID jobId, String contentSha256, int objectCount, long totalBytes, BatchCounters counters,
                          Stage stage);

    /** Giữ chỗ nội dung danh sách cho job này (LWT); trả job khác nếu nội dung đã được job đó giữ/ghi sổ. */
    Optional<UUID> reserveContent(UUID jobId, String contentSha256);

    /** Đọc các dòng VALID của lần thử theo thứ tự dòng, từng khối {@code chunkSize}. */
    void forEachValidItem(UUID jobId, int attemptNo, long rows, int chunkSize, Consumer<List<Item>> chunk);

    void updateStage(UUID jobId, Stage stage);
}
