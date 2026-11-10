package vn.teca.hddt.dataapi.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;

/**
 * Sổ hoàn thuế (Doris {@code HD_SO_HOAN}) — nhật ký N-N hóa đơn × mã công việc (MCV), trạng thái DA_HOAN/DA_HUY,
 * không xóa. Ghi idempotent theo lô (label Doris). Không đụng HOADON_HDR / HD_MST_SUMMARY.
 */
public interface RefundLedgerPort {

    /** Một dòng sổ hoàn. MHSO/SQDINH/NQDINH/TDCNHOAN chưa có trong hợp đồng ⇒ không có ở đây (NULL trên bảng). */
    static final class Entry {
        private final LocalDate tdlap;
        private final String nbmst;
        private final int khmshdon;
        private final String khhdon;
        private final long shdon;
        private final String mcv;
        private final String loaiHd;
        private final String cqt;
        private final String id;
        private final String msthso;
        private final BigDecimal tgtcthue;
        private final BigDecimal tgtthue;
        private final String thttltsuat;
        private final String ttmstnban;
        private final Integer tthai;
        private final LocalDateTime nky;
        private final long sttDong;
        private final String nguonFile;
        private final String shaFile;
        private final String clientId;

        public Entry(LocalDate tdlap, String nbmst, int khmshdon, String khhdon, long shdon, String mcv, String loaiHd,
                String cqt, String id, String msthso, BigDecimal tgtcthue, BigDecimal tgtthue, String thttltsuat,
                String ttmstnban, Integer tthai, LocalDateTime nky, long sttDong, String nguonFile, String shaFile,
                String clientId) {
            this.tdlap = tdlap;
            this.nbmst = nbmst;
            this.khmshdon = khmshdon;
            this.khhdon = khhdon;
            this.shdon = shdon;
            this.mcv = mcv;
            this.loaiHd = loaiHd;
            this.cqt = cqt;
            this.id = id;
            this.msthso = msthso;
            this.tgtcthue = tgtcthue;
            this.tgtthue = tgtthue;
            this.thttltsuat = thttltsuat;
            this.ttmstnban = ttmstnban;
            this.tthai = tthai;
            this.nky = nky;
            this.sttDong = sttDong;
            this.nguonFile = nguonFile;
            this.shaFile = shaFile;
            this.clientId = clientId;
        }

        public LocalDate tdlap() {
            return tdlap;
        }

        public String nbmst() {
            return nbmst;
        }

        public int khmshdon() {
            return khmshdon;
        }

        public String khhdon() {
            return khhdon;
        }

        public long shdon() {
            return shdon;
        }

        public String mcv() {
            return mcv;
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

        public String msthso() {
            return msthso;
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

        public long sttDong() {
            return sttDong;
        }

        public String nguonFile() {
            return nguonFile;
        }

        public String shaFile() {
            return shaFile;
        }

        public String clientId() {
            return clientId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) o;
            return Objects.equals(tdlap, other.tdlap)
                    && Objects.equals(nbmst, other.nbmst)
                    && khmshdon == other.khmshdon
                    && Objects.equals(khhdon, other.khhdon)
                    && shdon == other.shdon
                    && Objects.equals(mcv, other.mcv)
                    && Objects.equals(loaiHd, other.loaiHd)
                    && Objects.equals(cqt, other.cqt)
                    && Objects.equals(id, other.id)
                    && Objects.equals(msthso, other.msthso)
                    && Objects.equals(tgtcthue, other.tgtcthue)
                    && Objects.equals(tgtthue, other.tgtthue)
                    && Objects.equals(thttltsuat, other.thttltsuat)
                    && Objects.equals(ttmstnban, other.ttmstnban)
                    && Objects.equals(tthai, other.tthai)
                    && Objects.equals(nky, other.nky)
                    && sttDong == other.sttDong
                    && Objects.equals(nguonFile, other.nguonFile)
                    && Objects.equals(shaFile, other.shaFile)
                    && Objects.equals(clientId, other.clientId);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(tdlap);
            result = 31 * result + Objects.hashCode(nbmst);
            result = 31 * result + Integer.hashCode(khmshdon);
            result = 31 * result + Objects.hashCode(khhdon);
            result = 31 * result + Long.hashCode(shdon);
            result = 31 * result + Objects.hashCode(mcv);
            result = 31 * result + Objects.hashCode(loaiHd);
            result = 31 * result + Objects.hashCode(cqt);
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(msthso);
            result = 31 * result + Objects.hashCode(tgtcthue);
            result = 31 * result + Objects.hashCode(tgtthue);
            result = 31 * result + Objects.hashCode(thttltsuat);
            result = 31 * result + Objects.hashCode(ttmstnban);
            result = 31 * result + Objects.hashCode(tthai);
            result = 31 * result + Objects.hashCode(nky);
            result = 31 * result + Long.hashCode(sttDong);
            result = 31 * result + Objects.hashCode(nguonFile);
            result = 31 * result + Objects.hashCode(shaFile);
            result = 31 * result + Objects.hashCode(clientId);
            return result;
        }

        @Override
        public String toString() {
            return "Entry[tdlap=" + tdlap
                    + ", nbmst=" + nbmst
                    + ", khmshdon=" + khmshdon
                    + ", khhdon=" + khhdon
                    + ", shdon=" + shdon
                    + ", mcv=" + mcv
                    + ", loaiHd=" + loaiHd
                    + ", cqt=" + cqt
                    + ", id=" + id
                    + ", msthso=" + msthso
                    + ", tgtcthue=" + tgtcthue
                    + ", tgtthue=" + tgtthue
                    + ", thttltsuat=" + thttltsuat
                    + ", ttmstnban=" + ttmstnban
                    + ", tthai=" + tthai
                    + ", nky=" + nky
                    + ", sttDong=" + sttDong
                    + ", nguonFile=" + nguonFile
                    + ", shaFile=" + shaFile
                    + ", clientId=" + clientId + "]";
        }
    }

    /** Hóa đơn đang ở trạng thái DA_HOAN dưới mã công việc KHÁC {@code excludeMcv}: khóa → MCV. */
    Map<InvoiceKey, String> findActiveRefunds(Collection<DatedKey> keys, String excludeMcv);

    /** Ghi một khối dòng; gọi lại cùng (mcv, chunkNo) không tạo bản ghi trùng. */
    void write(String mcv, int chunkNo, List<Entry> entries);

    /** Số dòng DA_HOAN của mã công việc (đối chiếu sau khi ghi). */
    long countActive(String mcv, LocalDate minTdlap, LocalDate maxTdlap);
}
