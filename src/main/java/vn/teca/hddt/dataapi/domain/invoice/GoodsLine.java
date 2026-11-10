package vn.teca.hddt.dataapi.domain.invoice;

import java.math.BigDecimal;
import java.util.Objects;

/** Dòng hàng hóa dịch vụ (HOADON_DTL) cho HD3 dshhdvu. */
public final class GoodsLine {
    private final int stt;
    private final String ten;
    private final BigDecimal thtcthue;
    private final String ltsuat;
    private final BigDecimal tthue;

    public GoodsLine(int stt, String ten, BigDecimal thtcthue, String ltsuat, BigDecimal tthue) {
        this.stt = stt;
        this.ten = ten;
        this.thtcthue = thtcthue;
        this.ltsuat = ltsuat;
        this.tthue = tthue;
    }

    public int stt() {
        return stt;
    }

    public String ten() {
        return ten;
    }

    public BigDecimal thtcthue() {
        return thtcthue;
    }

    public String ltsuat() {
        return ltsuat;
    }

    public BigDecimal tthue() {
        return tthue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoodsLine)) {
            return false;
        }
        GoodsLine other = (GoodsLine) o;
        return stt == other.stt
                && Objects.equals(ten, other.ten)
                && Objects.equals(thtcthue, other.thtcthue)
                && Objects.equals(ltsuat, other.ltsuat)
                && Objects.equals(tthue, other.tthue);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(stt);
        result = 31 * result + Objects.hashCode(ten);
        result = 31 * result + Objects.hashCode(thtcthue);
        result = 31 * result + Objects.hashCode(ltsuat);
        result = 31 * result + Objects.hashCode(tthue);
        return result;
    }

    @Override
    public String toString() {
        return "GoodsLine[stt=" + stt
                + ", ten=" + ten
                + ", thtcthue=" + thtcthue
                + ", ltsuat=" + ltsuat
                + ", tthue=" + tthue + "]";
    }
}
