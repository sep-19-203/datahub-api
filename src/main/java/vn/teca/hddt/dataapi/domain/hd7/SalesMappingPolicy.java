package vn.teca.hddt.dataapi.domain.hd7;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import vn.teca.hddt.dataapi.domain.common.BusinessRuleNotConfirmedException;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Ánh xạ chỉ tiêu bán ra HD7 ← mã thuế suất (chốt 01/11, SI-09/10/11/41). Trường giá trị cộng `thtien`, trường thuế
 * cộng `tthue` của các mã được gán. Trường không gán mã nào (32b, 34a) ⇒ NULL "chưa có nguồn", không phải 0.
 * Mã có số liệu nhưng không thuộc chỉ tiêu nào (KHAC, không xác định) ⇒ IGNORE: trả về để ghi cảnh báo;
 * FAIL_IF_NONZERO: chặn. Một mã chỉ được gán cho tối đa 1 trường giá trị và 1 trường thuế (tránh cộng trùng).
 */
public final class SalesMappingPolicy {

    public enum UnassignedCodePolicy { IGNORE, FAIL_IF_NONZERO }

    /** Kết quả: giá trị từng trường (NULL = chưa có nguồn) + phần số liệu không thuộc chỉ tiêu nào theo mã. */
    public static final class Result {
        private final Map<SalesField, BigDecimal> values;
        private final Map<String, RateAmount> unassigned;

        public Result(Map<SalesField, BigDecimal> values, Map<String, RateAmount> unassigned) {
            values = Collections.unmodifiableMap(new EnumMap<>(values));
            unassigned = Jdk8Compat.mapCopyOf(unassigned);
            this.values = values;
            this.unassigned = unassigned;
        }

        public Map<SalesField, BigDecimal> values() {
            return values;
        }

        public Map<String, RateAmount> unassigned() {
            return unassigned;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Result)) {
                return false;
            }
            Result other = (Result) o;
            return Objects.equals(values, other.values)
                    && Objects.equals(unassigned, other.unassigned);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(values);
            result = 31 * result + Objects.hashCode(unassigned);
            return result;
        }

        @Override
        public String toString() {
            return "Result[values=" + values
                    + ", unassigned=" + unassigned + "]";
        }
    }

    private final Map<SalesField, Set<String>> codes;
    private final UnassignedCodePolicy unassignedPolicy;
    private final Set<String> amountCodes = new LinkedHashSet<>();
    private final Set<String> taxCodes = new LinkedHashSet<>();

    public SalesMappingPolicy(Map<SalesField, List<String>> mapping, UnassignedCodePolicy unassignedPolicy) {
        EnumMap<SalesField, Set<String>> copy = new EnumMap<>(SalesField.class);
        for (SalesField f : SalesField.values()) {
            if (!mapping.containsKey(f)) {
                throw new IllegalArgumentException("Cấu hình HD7 thiếu trường " + f.jsonName()
                        + " (để danh sách rỗng nếu chưa có nguồn)");
            }
            Set<String> normalized = new LinkedHashSet<>();
            List<String> raw = mapping.get(f);
            if (raw != null) {
                for (String c : raw) {
                    String n = TaxRateCode.normalize(c);
                    if (!(f.tax() ? taxCodes : amountCodes).add(n)) {
                        throw new IllegalArgumentException("Mã thuế suất '" + n + "' được gán cho nhiều chỉ tiêu "
                                + (f.tax() ? "thuế" : "giá trị") + " HD7 (cộng trùng)");
                    }
                    normalized.add(n);
                }
            }
            copy.put(f, Jdk8Compat.setCopyOf(normalized));
        }
        this.codes = copy;
        this.unassignedPolicy = unassignedPolicy;
    }

    public Result apply(RateBreakdown breakdown) {
        Map<String, RateAmount> unassigned = new LinkedHashMap<>();
        breakdown.byRate().forEach((code, v) -> {
            RateAmount left = new RateAmount(amountCodes.contains(code) ? BigDecimal.ZERO : v.amount(),
                    taxCodes.contains(code) ? BigDecimal.ZERO : v.tax());
            if (!left.isZero()) {
                unassigned.put(code, left);
            }
        });
        if (unassignedPolicy == UnassignedCodePolicy.FAIL_IF_NONZERO && !unassigned.isEmpty()) {
            throw new BusinessRuleNotConfirmedException(BusinessRuleNotConfirmedException.Kind.MAPPING, "SI-10",
                    "Có số liệu bán ra theo thuế suất chưa được gán chỉ tiêu tờ khai: " + unassigned.keySet());
        }
        Map<SalesField, BigDecimal> values = new HashMap<>();
        for (SalesField f : SalesField.values()) {
            Set<String> cs = codes.get(f);
            if (cs.isEmpty()) {
                values.put(f, null);
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (String c : cs) {
                RateAmount v = breakdown.get(c);
                sum = sum.add(f.tax() ? v.tax() : v.amount());
            }
            values.put(f, sum);
        }
        return new Result(values, unassigned);
    }
}
