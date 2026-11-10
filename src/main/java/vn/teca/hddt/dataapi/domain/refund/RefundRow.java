package vn.teca.hddt.dataapi.domain.refund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.TaxRateLine;

/** Một dòng file TMS v3 (TMS!C34–C46); {@code thttltsuatJson} = JSON nguyên văn (ghi sổ hoàn). */
public final class RefundRow {
    private final long lineNo;
    private final String nbmst;
    private final int khmshdon;
    private final String khhdon;
    private final long shdon;
    private final LocalDate tdlap;
    private final LocalDateTime nky;
    private final int tthai;
    private final String nbten;
    private final BigDecimal tgtcthue;
    private final BigDecimal tgtthue;
    private final List<TaxRateLine> thttltsuat;
    private final String ttmstnban;
    private final String thttltsuatJson;
    private final String lddctthe;

    public RefundRow(long lineNo, String nbmst, int khmshdon, String khhdon, long shdon, LocalDate tdlap,
            LocalDateTime nky, int tthai, String nbten, BigDecimal tgtcthue, BigDecimal tgtthue,
            List<TaxRateLine> thttltsuat, String ttmstnban, String thttltsuatJson, String lddctthe) {
        this.lineNo = lineNo;
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
        this.thttltsuat = thttltsuat;
        this.ttmstnban = ttmstnban;
        this.thttltsuatJson = thttltsuatJson;
        this.lddctthe = lddctthe;
    }

    public long lineNo() {
        return lineNo;
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

    public LocalDate tdlap() {
        return tdlap;
    }

    public LocalDateTime nky() {
        return nky;
    }

    public int tthai() {
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

    public String ttmstnban() {
        return ttmstnban;
    }

    public String thttltsuatJson() {
        return thttltsuatJson;
    }

    public String lddctthe() {
        return lddctthe;
    }

    public InvoiceKey key() {
        return new InvoiceKey(nbmst, khmshdon, khhdon, shdon);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefundRow)) {
            return false;
        }
        RefundRow other = (RefundRow) o;
        return lineNo == other.lineNo
                && Objects.equals(nbmst, other.nbmst)
                && khmshdon == other.khmshdon
                && Objects.equals(khhdon, other.khhdon)
                && shdon == other.shdon
                && Objects.equals(tdlap, other.tdlap)
                && Objects.equals(nky, other.nky)
                && tthai == other.tthai
                && Objects.equals(nbten, other.nbten)
                && Objects.equals(tgtcthue, other.tgtcthue)
                && Objects.equals(tgtthue, other.tgtthue)
                && Objects.equals(thttltsuat, other.thttltsuat)
                && Objects.equals(ttmstnban, other.ttmstnban)
                && Objects.equals(thttltsuatJson, other.thttltsuatJson)
                && Objects.equals(lddctthe, other.lddctthe);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(lineNo);
        result = 31 * result + Objects.hashCode(nbmst);
        result = 31 * result + Integer.hashCode(khmshdon);
        result = 31 * result + Objects.hashCode(khhdon);
        result = 31 * result + Long.hashCode(shdon);
        result = 31 * result + Objects.hashCode(tdlap);
        result = 31 * result + Objects.hashCode(nky);
        result = 31 * result + Integer.hashCode(tthai);
        result = 31 * result + Objects.hashCode(nbten);
        result = 31 * result + Objects.hashCode(tgtcthue);
        result = 31 * result + Objects.hashCode(tgtthue);
        result = 31 * result + Objects.hashCode(thttltsuat);
        result = 31 * result + Objects.hashCode(ttmstnban);
        result = 31 * result + Objects.hashCode(thttltsuatJson);
        result = 31 * result + Objects.hashCode(lddctthe);
        return result;
    }

    @Override
    public String toString() {
        return "RefundRow[lineNo=" + lineNo
                + ", nbmst=" + nbmst
                + ", khmshdon=" + khmshdon
                + ", khhdon=" + khhdon
                + ", shdon=" + shdon
                + ", tdlap=" + tdlap
                + ", nky=" + nky
                + ", tthai=" + tthai
                + ", nbten=" + nbten
                + ", tgtcthue=" + tgtcthue
                + ", tgtthue=" + tgtthue
                + ", thttltsuat=" + thttltsuat
                + ", ttmstnban=" + ttmstnban
                + ", thttltsuatJson=" + thttltsuatJson
                + ", lddctthe=" + lddctthe + "]";
    }
}
