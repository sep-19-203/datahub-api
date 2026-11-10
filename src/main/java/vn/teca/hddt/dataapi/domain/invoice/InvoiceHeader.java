package vn.teca.hddt.dataapi.domain.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Header hóa đơn đọc từ HOADON_HDR cho HD3 (kèm khóa vật lý để đọc HOADON_DTL). */
public final class InvoiceHeader {
    private final LocalDate tdlap;
    private final String cqt;
    private final String loaiHd;
    private final String id;
    private final String nbmst;
    private final Integer khmshdon;
    private final String khhdon;
    private final Long shdon;
    private final LocalDateTime nky;
    private final Integer tthai;
    private final String nbten;
    private final BigDecimal tgtcthue;
    private final BigDecimal tgtthue;
    private final List<TaxRateLine> thttltsuat;
    private final Integer bhphap;
    private final String bhpldo;
    private final LocalDateTime bhpngay;
    /** Lý do điều chỉnh/thay thế (API v3 HD3!C51 {@code lddctthe}); nguồn HOADON_HDR.GCHDGOC (SI-53, đề xuất). */
    private final String lddctthe;

    public InvoiceHeader(LocalDate tdlap, String cqt, String loaiHd, String id, String nbmst, Integer khmshdon,
            String khhdon, Long shdon, LocalDateTime nky, Integer tthai, String nbten, BigDecimal tgtcthue,
            BigDecimal tgtthue, List<TaxRateLine> thttltsuat, Integer bhphap, String bhpldo, LocalDateTime bhpngay,
            String lddctthe) {
        this.tdlap = tdlap;
        this.cqt = cqt;
        this.loaiHd = loaiHd;
        this.id = id;
        this.nbmst = nbmst;
        this.khmshdon = khmshdon;
        this.khhdon = khhdon;
        this.shdon = shdon;
        this.nky = nky;
        this.tthai = tthai;
        this.nbten = nbten;
        this.tgtcthue = tgtcthue;
        this.tgtthue = tgtthue;
        this.thttltsuat = thttltsuat;
        this.bhphap = bhphap;
        this.bhpldo = bhpldo;
        this.bhpngay = bhpngay;
        this.lddctthe = lddctthe;
    }

    public LocalDate tdlap() {
        return tdlap;
    }

    public String cqt() {
        return cqt;
    }

    public String loaiHd() {
        return loaiHd;
    }

    public String id() {
        return id;
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

    public List<TaxRateLine> thttltsuat() {
        return thttltsuat;
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

    public String lddctthe() {
        return lddctthe;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceHeader)) {
            return false;
        }
        InvoiceHeader other = (InvoiceHeader) o;
        return Objects.equals(tdlap, other.tdlap)
                && Objects.equals(cqt, other.cqt)
                && Objects.equals(loaiHd, other.loaiHd)
                && Objects.equals(id, other.id)
                && Objects.equals(nbmst, other.nbmst)
                && Objects.equals(khmshdon, other.khmshdon)
                && Objects.equals(khhdon, other.khhdon)
                && Objects.equals(shdon, other.shdon)
                && Objects.equals(nky, other.nky)
                && Objects.equals(tthai, other.tthai)
                && Objects.equals(nbten, other.nbten)
                && Objects.equals(tgtcthue, other.tgtcthue)
                && Objects.equals(tgtthue, other.tgtthue)
                && Objects.equals(thttltsuat, other.thttltsuat)
                && Objects.equals(bhphap, other.bhphap)
                && Objects.equals(bhpldo, other.bhpldo)
                && Objects.equals(bhpngay, other.bhpngay)
                && Objects.equals(lddctthe, other.lddctthe);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(tdlap);
        result = 31 * result + Objects.hashCode(cqt);
        result = 31 * result + Objects.hashCode(loaiHd);
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(nbmst);
        result = 31 * result + Objects.hashCode(khmshdon);
        result = 31 * result + Objects.hashCode(khhdon);
        result = 31 * result + Objects.hashCode(shdon);
        result = 31 * result + Objects.hashCode(nky);
        result = 31 * result + Objects.hashCode(tthai);
        result = 31 * result + Objects.hashCode(nbten);
        result = 31 * result + Objects.hashCode(tgtcthue);
        result = 31 * result + Objects.hashCode(tgtthue);
        result = 31 * result + Objects.hashCode(thttltsuat);
        result = 31 * result + Objects.hashCode(bhphap);
        result = 31 * result + Objects.hashCode(bhpldo);
        result = 31 * result + Objects.hashCode(bhpngay);
        result = 31 * result + Objects.hashCode(lddctthe);
        return result;
    }

    @Override
    public String toString() {
        return "InvoiceHeader[tdlap=" + tdlap
                + ", cqt=" + cqt
                + ", loaiHd=" + loaiHd
                + ", id=" + id
                + ", nbmst=" + nbmst
                + ", khmshdon=" + khmshdon
                + ", khhdon=" + khhdon
                + ", shdon=" + shdon
                + ", nky=" + nky
                + ", tthai=" + tthai
                + ", nbten=" + nbten
                + ", tgtcthue=" + tgtcthue
                + ", tgtthue=" + tgtthue
                + ", thttltsuat=" + thttltsuat
                + ", bhphap=" + bhphap
                + ", bhpldo=" + bhpldo
                + ", bhpngay=" + bhpngay
                + ", lddctthe=" + lddctthe + "]";
    }
}
