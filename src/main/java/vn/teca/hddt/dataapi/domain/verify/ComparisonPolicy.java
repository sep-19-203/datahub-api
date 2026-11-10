package vn.teca.hddt.dataapi.domain.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Quy tắc so sánh HD8 (SI-13 — CHƯA được nghiệp vụ duyệt; API HD8 bị chặn tới khi duyệt).
 * Tiền: |a − b| ≤ tolerance (mặc định 0, không làm tròn); null chỉ bằng null (không coi null = 0).
 * Chuỗi: so khớp tuyệt đối, không trim/không đổi hoa thường. Ngày/giờ: bằng nhau tuyệt đối.
 */
public final class ComparisonPolicy {
    private final List<CompareField> fields;
    private final BigDecimal moneyTolerance;

    public ComparisonPolicy(List<CompareField> fields, BigDecimal moneyTolerance) {
        fields = Jdk8Compat.listCopyOf(fields);
        Objects.requireNonNull(moneyTolerance, "moneyTolerance");
        if (moneyTolerance.signum() < 0) {
            throw new IllegalArgumentException("moneyTolerance phải >= 0");
        }
        this.fields = fields;
        this.moneyTolerance = moneyTolerance;
    }

    public List<CompareField> fields() {
        return fields;
    }

    public BigDecimal moneyTolerance() {
        return moneyTolerance;
    }

    public List<CompareField> differences(InvoiceRecord expected, InvoiceRecord actual) {
        List<CompareField> diff = new ArrayList<>();
        for (CompareField f : fields) {
            if (!same(f, f.value(expected), f.value(actual))) {
                diff.add(f);
            }
        }
        return diff;
    }

    private boolean same(CompareField f, Object a, Object b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (f.money()) {
            return ((BigDecimal) a).subtract((BigDecimal) b).abs().compareTo(moneyTolerance) <= 0;
        }
        return a.equals(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComparisonPolicy)) {
            return false;
        }
        ComparisonPolicy other = (ComparisonPolicy) o;
        return Objects.equals(fields, other.fields)
                && Objects.equals(moneyTolerance, other.moneyTolerance);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(fields);
        result = 31 * result + Objects.hashCode(moneyTolerance);
        return result;
    }

    @Override
    public String toString() {
        return "ComparisonPolicy[fields=" + fields
                + ", moneyTolerance=" + moneyTolerance + "]";
    }
}
