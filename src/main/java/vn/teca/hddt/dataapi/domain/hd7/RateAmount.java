package vn.teca.hddt.dataapi.domain.hd7;

import java.math.BigDecimal;
import java.util.Objects;

/** Tổng thành tiền (`thtien`) và tiền thuế (`tthue`) của một mã thuế suất; NULL nguồn ⇒ 0 (SUM bỏ qua NULL). */
public final class RateAmount {
    private final BigDecimal amount;
    private final BigDecimal tax;

    public static final RateAmount ZERO = new RateAmount(BigDecimal.ZERO, BigDecimal.ZERO);

    public RateAmount(BigDecimal amount, BigDecimal tax) {
        amount = amount == null ? BigDecimal.ZERO : amount;
        tax = tax == null ? BigDecimal.ZERO : tax;
        this.amount = amount;
        this.tax = tax;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal tax() {
        return tax;
    }

    public RateAmount plus(RateAmount o) {
        return new RateAmount(amount.add(o.amount), tax.add(o.tax));
    }

    public boolean isZero() {
        return amount.signum() == 0 && tax.signum() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RateAmount)) {
            return false;
        }
        RateAmount other = (RateAmount) o;
        return Objects.equals(amount, other.amount)
                && Objects.equals(tax, other.tax);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(amount);
        result = 31 * result + Objects.hashCode(tax);
        return result;
    }

    @Override
    public String toString() {
        return "RateAmount[amount=" + amount
                + ", tax=" + tax + "]";
    }
}
