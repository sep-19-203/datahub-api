package vn.teca.hddt.dataapi.domain.common;

import java.util.regex.Pattern;

/**
 * Mã số thuế trong tham số API: 10 số, 10 số + "-" + 3 số (đơn vị phụ thuộc), hoặc 12 số (MST cá nhân theo số định danh).
 * Đề xuất SI-32/SI-23 (đặc tả không quy định định dạng). Không dùng để lọc dữ liệu đã nạp (cột MST nguồn rộng hơn).
 */
public final class TaxCode {
    private static final Pattern VALID = Pattern.compile("^(\\d{10}(-\\d{3})?|\\d{12})$");

    private TaxCode() {
    }

    public static boolean isValid(String mst) {
        return mst != null && VALID.matcher(mst).matches();
    }

    public static String require(String field, String mst) {
        if (!isValid(mst)) {
            throw new DomainValidationException(field, field + " không đúng định dạng mã số thuế");
        }
        return mst;
    }

    /** Che MST khi ghi log (không lộ dữ liệu NNT không cần thiết). */
    public static String mask(String mst) {
        if (mst == null || mst.length() < 6) {
            return "***";
        }
        return mst.substring(0, 3) + "****" + mst.substring(mst.length() - 2);
    }
}
