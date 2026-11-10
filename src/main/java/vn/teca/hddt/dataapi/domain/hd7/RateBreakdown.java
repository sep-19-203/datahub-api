package vn.teca.hddt.dataapi.domain.hd7;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Tổng theo mã thuế suất đã chuẩn hóa ({@link TaxRateCode}) của tập HĐ hợp lệ một chiều (mua vào hoặc bán ra) của
 * một MST trong khoảng kỳ — gom từ THTTLTSUAT, HĐ thiếu THTTLTSUAT từ dòng hàng HOADON_DTL, HĐ không có thuế suất ở
 * đâu cả thì suy từ tỷ lệ tiền thuế/giá trị trên header. {@code invoiceCount} = số HĐ trong phạm vi. Không có HĐ ⇒ map rỗng.
 */
public final class RateBreakdown {
    private final Map<String, RateAmount> byRate;
    private final long invoiceCount;

    public RateBreakdown(Map<String, RateAmount> byRate, long invoiceCount) {
        this.byRate = Jdk8Compat.mapCopyOf(byRate);
        this.invoiceCount = invoiceCount;
    }

    public Map<String, RateAmount> byRate() {
        return byRate;
    }

    public long invoiceCount() {
        return invoiceCount;
    }

    public RateAmount get(String normalizedCode) {
        return byRate.getOrDefault(normalizedCode, RateAmount.ZERO);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, RateAmount> byRate = new LinkedHashMap<>();
        private long invoiceCount;

        /** Cộng dồn theo mã đã chuẩn hóa ("10%" và "10.00%" về cùng một nhóm). */
        public Builder add(String rawCode, BigDecimal amount, BigDecimal tax) {
            byRate.merge(TaxRateCode.normalize(rawCode), new RateAmount(amount, tax), RateAmount::plus);
            return this;
        }

        public Builder invoiceCount(long n) {
            this.invoiceCount = n;
            return this;
        }

        public RateBreakdown build() {
            return new RateBreakdown(byRate, invoiceCount);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RateBreakdown)) {
            return false;
        }
        RateBreakdown other = (RateBreakdown) o;
        return Objects.equals(byRate, other.byRate) && invoiceCount == other.invoiceCount;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(byRate) + Long.hashCode(invoiceCount);
    }

    @Override
    public String toString() {
        return "RateBreakdown[byRate=" + byRate + ", invoiceCount=" + invoiceCount + "]";
    }
}
