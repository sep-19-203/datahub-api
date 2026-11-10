package vn.teca.hddt.dataapi.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/** Tra theo lô cho HD8/TMS (không truy vấn từng dòng). */
public interface InvoiceLookupPort {

    static final class DatedKey {
        private final InvoiceKey key;
        private final LocalDate tdlap;

        public DatedKey(InvoiceKey key, LocalDate tdlap) {
            this.key = key;
            this.tdlap = tdlap;
        }

        public InvoiceKey key() {
            return key;
        }

        public LocalDate tdlap() {
            return tdlap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DatedKey)) {
                return false;
            }
            DatedKey other = (DatedKey) o;
            return Objects.equals(key, other.key)
                    && Objects.equals(tdlap, other.tdlap);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(key);
            result = 31 * result + Objects.hashCode(tdlap);
            return result;
        }

        @Override
        public String toString() {
            return "DatedKey[key=" + key
                    + ", tdlap=" + tdlap + "]";
        }
    }

    /** HĐ mua vào của {@code nmmst} có TDLAP ∈ [tngay, dngay] và khóa thuộc tập {@code keys}. */
    List<InvoiceRecord> findPurchases(String nmmst, LocalDate tngay, LocalDate dngay, Collection<InvoiceKey> keys);

    /**
     * DB-P1-02 bước A — MỘT truy vấn luồng mỗi job trên HD_MST_LIST (MST = nmmst, MUA_VAO, IS_ELIGIBLE = 1, TDLAP ∈ [tngay,
     * dngay]; không suy KY vì kỳ mua vào theo NKY→NCMA→TDLAP), chỉ giữ dòng có {@link InvoiceKey#lookupFingerprint} được
     * {@code wanted} chấp nhận (khóa của file đầu vào).
     */
    PurchaseCandidates indexPurchases(String nmmst, LocalDate tngay, LocalDate dngay, LongPredicate wanted);

    /** Số ứng viên bước A sẽ đọc (COUNT phía Doris, không kéo dòng) — để chọn chỉ mục hay tra theo lô. */
    long countPurchaseCandidates(String nmmst, LocalDate tngay, LocalDate dngay);

    /**
     * DB-P1-02 bước B — HĐ có khóa thuộc {@code keys} (một lô), tra theo khóa vật lý literal lấy từ chỉ mục (không quét lại
     * LIST theo người mua). Cùng kết quả {@link #findPurchases} khi dữ liệu không đổi giữa hai bước.
     */
    List<InvoiceRecord> fetchPurchases(String nmmst, PurchaseCandidates index, Collection<InvoiceKey> keys);

    /** Hóa đơn khớp cho sổ hoàn: khóa vật lý Doris + MST người mua (NNT được hoàn). */
    static final class RefundTarget {
        private final InvoiceKey key;
        private final LocalDate tdlap;
        private final String loaiHd;
        private final String cqt;
        private final String id;
        private final String nmmst;

        public RefundTarget(InvoiceKey key, LocalDate tdlap, String loaiHd, String cqt, String id, String nmmst) {
            this.key = key;
            this.tdlap = tdlap;
            this.loaiHd = loaiHd;
            this.cqt = cqt;
            this.id = id;
            this.nmmst = nmmst;
        }

        public InvoiceKey key() {
            return key;
        }

        public LocalDate tdlap() {
            return tdlap;
        }

        public String loaiHd() {
            return loaiHd;
        }

        public String cqt() {
            return cqt;
        }

        public String id() {
            return id;
        }

        public String nmmst() {
            return nmmst;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RefundTarget)) {
                return false;
            }
            RefundTarget other = (RefundTarget) o;
            return Objects.equals(key, other.key)
                    && Objects.equals(tdlap, other.tdlap)
                    && Objects.equals(loaiHd, other.loaiHd)
                    && Objects.equals(cqt, other.cqt)
                    && Objects.equals(id, other.id)
                    && Objects.equals(nmmst, other.nmmst);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(key);
            result = 31 * result + Objects.hashCode(tdlap);
            result = 31 * result + Objects.hashCode(loaiHd);
            result = 31 * result + Objects.hashCode(cqt);
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(nmmst);
            return result;
        }

        @Override
        public String toString() {
            return "RefundTarget[key=" + key
                    + ", tdlap=" + tdlap
                    + ", loaiHd=" + loaiHd
                    + ", cqt=" + cqt
                    + ", id=" + id
                    + ", nmmst=" + nmmst + "]";
        }
    }

    /** HĐ theo khóa người bán + ngày lập (TMS), một truy vấn cho cả lô. */
    List<RefundTarget> findRefundTargets(Collection<DatedKey> keys);
}
