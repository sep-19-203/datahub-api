package vn.teca.hddt.dataapi.domain.verify;

import java.util.List;
import java.util.Objects;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Kết luận đối soát một dòng. {@code actual} = hóa đơn Doris cùng khóa (null khi không tồn tại / nhiều bản ghi) — dùng cho
 * thống kê; file kết quả HD8 ghi NGUYÊN DÒNG ĐẦU VÀO của mọi dòng không hợp lệ (SI-14, người dùng chốt 01/11).
 */
public final class Verdict {
    private final Outcome outcome;
    private final List<CompareField> differences;
    private final InvoiceRecord actual;

    public Verdict(Outcome outcome, List<CompareField> differences, InvoiceRecord actual) {
        this.outcome = outcome;
        this.differences = differences;
        this.actual = actual;
    }

    public Outcome outcome() {
        return outcome;
    }

    public List<CompareField> differences() {
        return differences;
    }

    public InvoiceRecord actual() {
        return actual;
    }

    public boolean discrepancy() {
        return outcome != Outcome.MATCH;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Verdict)) {
            return false;
        }
        Verdict other = (Verdict) o;
        return Objects.equals(outcome, other.outcome)
                && Objects.equals(differences, other.differences)
                && Objects.equals(actual, other.actual);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(outcome);
        result = 31 * result + Objects.hashCode(differences);
        result = 31 * result + Objects.hashCode(actual);
        return result;
    }

    @Override
    public String toString() {
        return "Verdict[outcome=" + outcome
                + ", differences=" + differences
                + ", actual=" + actual + "]";
    }
}
