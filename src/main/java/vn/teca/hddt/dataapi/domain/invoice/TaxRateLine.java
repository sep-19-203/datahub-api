package vn.teca.hddt.dataapi.domain.invoice;

import java.math.BigDecimal;
import java.util.Objects;

/** Một phần tử của THTTLTSUAT (tổng hợp theo thuế suất): tsuat giữ nguyên chuỗi nguồn (vd "8%", "KCT"). */
public final class TaxRateLine {
    private final String tsuat;
    private final BigDecimal thtien;
    private final BigDecimal tthue;

    public TaxRateLine(String tsuat, BigDecimal thtien, BigDecimal tthue) {
        this.tsuat = tsuat;
        this.thtien = thtien;
        this.tthue = tthue;
    }

    public String tsuat() {
        return tsuat;
    }

    public BigDecimal thtien() {
        return thtien;
    }

    public BigDecimal tthue() {
        return tthue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaxRateLine)) {
            return false;
        }
        TaxRateLine other = (TaxRateLine) o;
        return Objects.equals(tsuat, other.tsuat)
                && Objects.equals(thtien, other.thtien)
                && Objects.equals(tthue, other.tthue);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(tsuat);
        result = 31 * result + Objects.hashCode(thtien);
        result = 31 * result + Objects.hashCode(tthue);
        return result;
    }

    @Override
    public String toString() {
        return "TaxRateLine[tsuat=" + tsuat
                + ", thtien=" + thtien
                + ", tthue=" + tthue + "]";
    }
}
