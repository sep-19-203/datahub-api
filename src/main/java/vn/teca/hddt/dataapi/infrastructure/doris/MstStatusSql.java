package vn.teca.hddt.dataapi.infrastructure.doris;

import vn.teca.hddt.dataapi.config.HddtProperties;

/**
 * Bọc nguồn trạng thái MST cấu hình (SI-24, SI-65) thành bảng dẫn xuất 1 dòng/MST: (MST, TRANG_THAI chuỗi).
 *
 * <p>Nguồn phải trả 3 cột {@code (MST, MST_CU, TRANG_THAI)} — mặc định view {@code doris.NNT_ALL_STATUS} (MV của
 * NNT_HIS hiện hành). MST trên hóa đơn có thể là MST CŨ của NNT (đã đổi MST) ⇒ tra theo 2 nhánh:
 * <ol>
 *   <li>trùng {@code MST} (trực tiếp) — ưu tiên;</li>
 *   <li>không có dòng trực tiếp ⇒ trùng {@code MST_CU} (MST cũ) ⇒ lấy trạng thái của NNT mang MST mới.</li>
 * </ol>
 * Nhiều dòng cùng nhánh (một MST nhiều PARTNER, hoặc một MST cũ gắn với nhiều MST mới) ⇒ MAX (TD-API-20, đề xuất).
 * Nguồn không có MST cũ thì trả {@code NULL AS MST_CU}.
 *
 * <p>{@code keyFilter} (TD-API-39): biểu thức sau {@code IN} giới hạn các MST cần tra — danh sách {@code (?, ?)} hoặc truy
 * vấn con — để Doris lọc nguồn TRƯỚC khi gộp, không gộp cả bảng NNT mỗi lần xuất. Biểu thức xuất hiện 2 lần (mỗi nhánh
 * một lần) ⇒ người gọi truyền tham số của nó 2 lần, trước tham số của mệnh đề WHERE ngoài.
 * SQL nguồn là cấu hình tin cậy (không bao giờ từ request).
 */
public final class MstStatusSql {
    private MstStatusSql() {
    }

    /** Toàn bộ nguồn (không giới hạn MST) — chỉ dùng khi không xác định được tập MST cần tra. */
    public static String derivedTable(HddtProperties.MstStatus cfg) {
        return derivedTable(cfg, null);
    }

    /**
     * @param keyFilter {@code null} = không giới hạn; ngược lại là biểu thức đặt sau {@code IN}, vd {@code "(?, ?)"} hoặc
     *                  {@code "(SELECT l.NBMST FROM ... WHERE ...)"}; mỗi tham số {@code ?} trong đó phải được truyền 2 lần.
     */
    public static String derivedTable(HddtProperties.MstStatus cfg, String keyFilter) {
        if (!cfg.configured()) {
            throw new IllegalStateException("hddt.mst-status.source-sql chưa cấu hình");
        }
        String src = cfg.sourceSql().trim();
        if (src.endsWith(";")) {
            throw new IllegalStateException("hddt.mst-status.source-sql không được kết thúc bằng ';'");
        }
        String direct = keyFilter == null ? "" : " WHERE st0.MST IN " + keyFilter;
        String old = keyFilter == null ? " WHERE st0.MST_CU IS NOT NULL AND st0.MST_CU <> ''"
                : " WHERE st0.MST_CU IN " + keyFilter + " AND st0.MST_CU <> ''";
        return "(SELECT st1.MST AS MST,"
                + " IF(MAX(st1.TRUC_TIEP) = 1, MAX(IF(st1.TRUC_TIEP = 1, st1.TRANG_THAI, NULL)), MAX(st1.TRANG_THAI))"
                + " AS TRANG_THAI FROM ("
                + "SELECT st0.MST AS MST, CAST(st0.TRANG_THAI AS STRING) AS TRANG_THAI, 1 AS TRUC_TIEP FROM (" + src
                + ") st0" + direct
                + " UNION ALL SELECT st0.MST_CU AS MST, CAST(st0.TRANG_THAI AS STRING) AS TRANG_THAI, 0 AS TRUC_TIEP FROM ("
                + src + ") st0" + old
                + ") st1 GROUP BY st1.MST)";
    }
}
