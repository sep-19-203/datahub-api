package vn.teca.hddt.dataapi.domain.hd7;

import java.math.BigDecimal;
import java.util.Objects;

/** Tổng mua vào [23]/[24] (mã thuế suất `hddt.hd7.mvao-tsuat`) và số HĐ mua vào hợp lệ — SI-55 (04/11). */
public final class PurchaseTotals {
    private final BigDecimal pretax;
    private final BigDecimal tax;
    private final long invoiceCount;

    public PurchaseTotals(BigDecimal pretax, BigDecimal tax, long invoiceCount) {
        this.pretax = pretax;
        this.tax = tax;
        this.invoiceCount = invoiceCount;
    }

    public BigDecimal pretax() {
        return pretax;
    }

    public BigDecimal tax() {
        return tax;
    }

    public long invoiceCount() {
        return invoiceCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PurchaseTotals)) {
            return false;
        }
        PurchaseTotals other = (PurchaseTotals) o;
        return Objects.equals(pretax, other.pretax)
                && Objects.equals(tax, other.tax)
                && invoiceCount == other.invoiceCount;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(pretax);
        result = 31 * result + Objects.hashCode(tax);
        result = 31 * result + Long.hashCode(invoiceCount);
        return result;
    }

    @Override
    public String toString() {
        return "PurchaseTotals[pretax=" + pretax
                + ", tax=" + tax
                + ", invoiceCount=" + invoiceCount + "]";
    }
}
