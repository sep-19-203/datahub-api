package vn.teca.hddt.dataapi.domain.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Bản ghi dùng chung cho HD8: dòng đầu vào (file HD1 v3, SI-12/SI-65) và hóa đơn đọc từ Doris — tên trường đúng đặc tả HD1/HD8.
 * {@code nky} vẫn giữ dù đã gạch ngang trong v3 (không xuất — người dùng 08/11); 13 trường số tiền theo thuế suất pct* không
 * có ở đây (đã bỏ khỏi mã 06/11, SI-51; HD8 chỉ còn khai báo cột ẩn trong {@code CsvLayouts.HD8_RESULT}).
 * {@code thttltsuat}: JSON nguyên văn (Doris {@code CAST(THTTLTSUAT AS STRING)} / văn bản trong file NNT).
 * {@code loaiHd}/{@code id}: khóa vật lý Doris (chỉ có khi đọc từ Doris).
 */
public final class InvoiceRecord {
    private final String nbmst;
    private final Integer khmshdon;
    private final String khhdon;
    private final Long shdon;
    private final LocalDate tdlap;
    private final LocalDateTime nky;
    private final Integer tthai;
    private final String nbten;
    private final BigDecimal tgtcthue;
    private final BigDecimal tgtthue;
    private final Integer bhphap;
    private final String bhpldo;
    private final LocalDateTime bhpngay;
    private final String ttmstnban;
    private final String loaiHd;
    private final String id;
    private final String thttltsuat;
    private final String lddctthe;

    public InvoiceRecord(String nbmst, Integer khmshdon, String khhdon, Long shdon, LocalDate tdlap, LocalDateTime nky,
            Integer tthai, String nbten, BigDecimal tgtcthue, BigDecimal tgtthue,
            Integer bhphap, String bhpldo, LocalDateTime bhpngay, String ttmstnban, String loaiHd, String id) {
        this(nbmst, khmshdon, khhdon, shdon, tdlap, nky, tthai, nbten, tgtcthue, tgtthue, bhphap, bhpldo, bhpngay, ttmstnban,
                loaiHd, id, null, null);
    }

    public InvoiceRecord(String nbmst, Integer khmshdon, String khhdon, Long shdon, LocalDate tdlap, LocalDateTime nky,
            Integer tthai, String nbten, BigDecimal tgtcthue, BigDecimal tgtthue,
            Integer bhphap, String bhpldo, LocalDateTime bhpngay, String ttmstnban, String loaiHd, String id,
            String thttltsuat, String lddctthe) {
        this.nbmst = nbmst;
        this.khmshdon = khmshdon;
        this.khhdon = khhdon;
        this.shdon = shdon;
        this.tdlap = tdlap;
        this.nky = nky;
        this.tthai = tthai;
        this.nbten = nbten;
        this.tgtcthue = tgtcthue;
        this.tgtthue = tgtthue;
        this.bhphap = bhphap;
        this.bhpldo = bhpldo;
        this.bhpngay = bhpngay;
        this.ttmstnban = ttmstnban;
        this.loaiHd = loaiHd;
        this.id = id;
        this.thttltsuat = thttltsuat;
        this.lddctthe = lddctthe;
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

    public LocalDateTime nky() {
        return nky;
    }

    public Integer tthai() {
        return tthai;
    }

    public String nbten() {
        return nbten;
    }

    public BigDecimal tgtcthue() {
        return tgtcthue;
    }

    public BigDecimal tgtthue() {
        return tgtthue;
    }

    public Integer bhphap() {
        return bhphap;
    }

    public String bhpldo() {
        return bhpldo;
    }

    public LocalDateTime bhpngay() {
        return bhpngay;
    }

    public String ttmstnban() {
        return ttmstnban;
    }

    public String loaiHd() {
        return loaiHd;
    }

    public String id() {
        return id;
    }

    public String thttltsuat() {
        return thttltsuat;
    }

    public String lddctthe() {
        return lddctthe;
    }

    public InvoiceKey key() {
        return new InvoiceKey(nbmst, khmshdon == null ? -1 : khmshdon, khhdon, shdon == null ? -1 : shdon);
    }

    public InvoiceRecord withTtmstnban(String status) {
        return new InvoiceRecord(nbmst, khmshdon, khhdon, shdon, tdlap, nky, tthai, nbten, tgtcthue, tgtthue,
                bhphap, bhpldo, bhpngay, status, loaiHd, id, thttltsuat, lddctthe);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceRecord)) {
            return false;
        }
        InvoiceRecord other = (InvoiceRecord) o;
        return Objects.equals(nbmst, other.nbmst)
                && Objects.equals(khmshdon, other.khmshdon)
                && Objects.equals(khhdon, other.khhdon)
                && Objects.equals(shdon, other.shdon)
                && Objects.equals(tdlap, other.tdlap)
                && Objects.equals(nky, other.nky)
                && Objects.equals(tthai, other.tthai)
                && Objects.equals(nbten, other.nbten)
                && Objects.equals(tgtcthue, other.tgtcthue)
                && Objects.equals(tgtthue, other.tgtthue)
                && Objects.equals(bhphap, other.bhphap)
                && Objects.equals(bhpldo, other.bhpldo)
                && Objects.equals(bhpngay, other.bhpngay)
                && Objects.equals(ttmstnban, other.ttmstnban)
                && Objects.equals(loaiHd, other.loaiHd)
                && Objects.equals(id, other.id)
                && Objects.equals(thttltsuat, other.thttltsuat)
                && Objects.equals(lddctthe, other.lddctthe);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(nbmst);
        result = 31 * result + Objects.hashCode(khmshdon);
        result = 31 * result + Objects.hashCode(khhdon);
        result = 31 * result + Objects.hashCode(shdon);
        result = 31 * result + Objects.hashCode(tdlap);
        result = 31 * result + Objects.hashCode(nky);
        result = 31 * result + Objects.hashCode(tthai);
        result = 31 * result + Objects.hashCode(nbten);
        result = 31 * result + Objects.hashCode(tgtcthue);
        result = 31 * result + Objects.hashCode(tgtthue);
        result = 31 * result + Objects.hashCode(bhphap);
        result = 31 * result + Objects.hashCode(bhpldo);
        result = 31 * result + Objects.hashCode(bhpngay);
        result = 31 * result + Objects.hashCode(ttmstnban);
        result = 31 * result + Objects.hashCode(loaiHd);
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(thttltsuat);
        result = 31 * result + Objects.hashCode(lddctthe);
        return result;
    }

    @Override
    public String toString() {
        return "InvoiceRecord[nbmst=" + nbmst
                + ", khmshdon=" + khmshdon
                + ", khhdon=" + khhdon
                + ", shdon=" + shdon
                + ", tdlap=" + tdlap
                + ", nky=" + nky
                + ", tthai=" + tthai
                + ", nbten=" + nbten
                + ", tgtcthue=" + tgtcthue
                + ", tgtthue=" + tgtthue
                + ", bhphap=" + bhphap
                + ", bhpldo=" + bhpldo
                + ", bhpngay=" + bhpngay
                + ", ttmstnban=" + ttmstnban
                + ", loaiHd=" + loaiHd
                + ", id=" + id
                + ", thttltsuat=" + thttltsuat
                + ", lddctthe=" + lddctthe + "]";
    }
}
