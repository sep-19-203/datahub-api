package vn.teca.hddt.dataapi.domain.hd7;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa mã thuế suất `tsuat` (THTTLTSUAT / HOADON_DTL.LTSUAT) để gom nhóm: chữ thường, bỏ khoảng trắng;
 * dạng số ("10", "10.00%", " 8 %") ⇒ "10%", "8%". Mã chữ giữ nguyên (kct, kkknt, khac:3.5%). Rỗng ⇒ {@link #UNKNOWN}.
 */
public final class TaxRateCode {

    /** Hóa đơn/dòng không xác định được thuế suất (không có tsuat, hoặc HĐ không có dòng hàng hợp lệ). */
    public static final String UNKNOWN = "(khong-xac-dinh)";

    private static final Pattern NUMERIC = Pattern.compile("\\d+(\\.\\d+)?%?");

    private TaxRateCode() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String s = raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return UNKNOWN;
        }
        if (NUMERIC.matcher(s).matches()) {
            String n = s.endsWith("%") ? s.substring(0, s.length() - 1) : s;
            return new BigDecimal(n).stripTrailingZeros().toPlainString() + "%";
        }
        return s;
    }
}
