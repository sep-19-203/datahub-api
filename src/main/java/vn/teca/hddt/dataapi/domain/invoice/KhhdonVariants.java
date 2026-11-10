package vn.teca.hddt.dataapi.domain.invoice;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Biến thể ký hiệu hóa đơn chứa 'Đ' (U+0110) và 'Ð' (U+00D0) — hai ký tự trông giống nhau nhưng khác mã
 * (INV_Khmshdon_dacbiet.xlsx, quy tắc KHHDON_LATIN_ETH / KHHDON_VIET_D_STROKE của backfill). Dữ liệu đích giữ nguyên
 * ký tự nguồn, nên tra cứu HD3 thử cả hai biến thể; nếu ra nhiều hóa đơn thì báo mơ hồ (SI-18), không tự chọn.
 */
public final class KhhdonVariants {
    static final char VIET_D_STROKE = 'Đ';
    static final char LATIN_ETH = 'Ð';

    private KhhdonVariants() {
    }

    public static Set<String> of(String khhdon) {
        Set<String> out = new LinkedHashSet<>();
        out.add(khhdon);
        if (khhdon.indexOf(VIET_D_STROKE) >= 0) {
            out.add(khhdon.replace(VIET_D_STROKE, LATIN_ETH));
        }
        if (khhdon.indexOf(LATIN_ETH) >= 0) {
            out.add(khhdon.replace(LATIN_ETH, VIET_D_STROKE));
        }
        return out;
    }
}
