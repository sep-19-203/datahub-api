package vn.teca.hddt.dataapi.infrastructure.doris;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vn.teca.hddt.dataapi.domain.csv.CsvColumn;
import vn.teca.hddt.dataapi.domain.csv.CsvLayout;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;

/**
 * Sinh biểu thức SQL tạo NGUYÊN một dòng CSV (RFC 4180) trong Doris, để OUTFILE chỉ ghi một cột và không phụ thuộc
 * khả năng escape CSV của Doris. Quy tắc trùng {@code application.csv.CsvText} (SI-35): chuỗi bọc nháy kép và nhân đôi
 * nháy kép bên trong; số = CAST(... AS STRING) (DECIMAL giữ nguyên scale, không mũ); ngày/giờ DATE_FORMAT; NULL ⇒ ''.
 * Mọi phần tử đều khác NULL (IFNULL) vì CONCAT_WS bỏ qua NULL ⇒ lệch cột.
 */
public final class CsvLineSql {

    /**
     * Đánh dấu nguồn chỉ có ở HOADON_HDR (HD_MST_LIST v5 không có cột tương ứng — THTTLTSUAT, GCHDGOC). Bản HDR bỏ dấu
     * này; bản LIST không có cột đó ⇒ {@link #listCovers} = false và nơi gọi phải đọc HDR. (Hằng số biên dịch — khai báo
     * trước các map tĩnh bên dưới.)
     */
    private static final String HDR_ONLY_PREFIX = "@hdr:";

    /** Nguồn từng cột: biểu thức cột Doris (alias h = HOADON_HDR, s = trạng thái MST) hoặc null = không có nguồn. */
    static final Map<String, String> HD1_SOURCES = sources(false);
    static final Map<String, String> HD5_SOURCES = sources(true);
    /**
     * DB-P1-03 (TD-API-34): nguồn từ HD_MST_LIST DDL v5 (alias l) — cùng giá trị HDR do pipeline chép (toolkit TD-43, đối
     * soát LIST_CONTENT_VS_HDR); tên đối tác = l.TEN_DOI_TAC (MUA_VAO: NBTEN, BAN_RA: NMTEN).
     */
    static final Map<String, String> HD1_LIST_SOURCES = listSources(false);
    static final Map<String, String> HD5_LIST_SOURCES = listSources(true);

    private CsvLineSql() {
    }

    private static Map<String, String> sources(boolean sales) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : rawSources(sales).entrySet()) {
            String v = e.getValue();
            m.put(e.getKey(), v != null && v.startsWith(HDR_ONLY_PREFIX) ? v.substring(HDR_ONLY_PREFIX.length()) : v);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    private static Map<String, String> rawSources(boolean sales) {
        String statusExpr = "s.TRANG_THAI";
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        m.put("nbmst", "h.NBMST");
        m.put("khmshdon", "h.KHMSHDON");
        m.put("khhdon", "h.KHHDON");
        m.put("shdon", "h.SHDON");
        m.put("tdlap", "h.TDLAP");
        m.put("nky", "h.NKY");
        m.put("tthai", "h.TTHAI");
        m.put("nbten", "h.NBTEN");
        m.put("nmten", "h.NMTEN");          // SI-39: tên trên hóa đơn (DKT không có trong datahub_hddt)
        m.put("nmmst", "h.NMMST");
        m.put("tgtcthue", "h.TGTCTHUE");
        m.put("tgtthue", "h.TGTTHUE");
        m.put("tgtttbso", "h.TGTTTBSO");
        // HD1 v3 (SI-52): JSON nguyên văn như HD3 (mảng {tsuat, thtien, tthue}); VARIANT JSON null ⇒ rỗng. Chỉ có ở HDR.
        m.put("thttltsuat", HDR_ONLY_PREFIX + "NULLIF(CAST(h.THTTLTSUAT AS STRING), 'null')");
        m.put("lddctthe", HDR_ONLY_PREFIX + "h.GCHDGOC"); // HD1 v3: lý do điều chỉnh/thay thế — SI-53, nguồn đề xuất
        m.put("bhphap", "h.BHPHAP");
        m.put("bhpldo", "h.BHPLDO");
        m.put("bhpngay", "h.BHPNGAY");
        m.put("ttmstnban", sales ? null : statusExpr);
        m.put("ttmstnmua", sales ? statusExpr : null);
        return m;
    }

    private static Map<String, String> listSources(boolean sales) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : rawSources(sales).entrySet()) {
            String v = e.getValue();
            if (v != null && v.startsWith(HDR_ONLY_PREFIX)) {
                continue; // LIST v5 không có cột ⇒ không có ánh xạ (listCovers = false)
            }
            m.put(e.getKey(), v != null && v.startsWith("h.") ? "l." + v.substring(2) : v);
        }
        m.put("nbten", sales ? null : "l.TEN_DOI_TAC");
        m.put("nmten", sales ? "l.TEN_DOI_TAC" : null);
        return java.util.Collections.unmodifiableMap(m);
    }

    public static String lineExpression(CsvLayout layout) {
        return lineExpression(layout, false);
    }

    /**
     * true ⇒ mọi cột của bố cục có nguồn trên HD_MST_LIST v5 (list-serving dùng được). HD1 v3 có {@code thttltsuat},
     * {@code lddctthe} chỉ ở HOADON_HDR ⇒ false (SI-52, KI-API-32).
     */
    public static boolean listCovers(CsvLayout layout) {
        Map<String, String> src = layout == CsvLayouts.HD5_SALES ? HD5_LIST_SOURCES : HD1_LIST_SOURCES;
        for (CsvColumn c : layout.columns()) {
            if (!src.containsKey(c.name())) {
                return false;
            }
        }
        return true;
    }

    /** @param fromList true ⇒ cột lấy từ HD_MST_LIST v5 (alias l), không cần HOADON_HDR */
    public static String lineExpression(CsvLayout layout, boolean fromList) {
        boolean sales = layout == CsvLayouts.HD5_SALES;
        Map<String, String> src = fromList ? (sales ? HD5_LIST_SOURCES : HD1_LIST_SOURCES) : (sales ? HD5_SOURCES : HD1_SOURCES);
        List<String> parts = new ArrayList<>(layout.size());
        for (CsvColumn c : layout.columns()) {
            if (!src.containsKey(c.name())) {
                throw new IllegalStateException("Thiếu ánh xạ nguồn cho cột " + c.name() + " của " + layout.name());
            }
            parts.add(element(c, src.get(c.name())));
        }
        return "CONCAT_WS(','," + String.join(",", parts) + ")";
    }

    static String element(CsvColumn c, String expr) {
        if (expr == null) {
            if (c.required()) {
                throw new IllegalStateException("Cột bắt buộc " + c.name() + " không có nguồn");
            }
            return "''";
        }
        switch (c.kind()) {
            case STRING:
            case JSON:
                return "IFNULL(CONCAT('\"', REPLACE(" + expr + ", '\"', '\"\"'), '\"'), '')";
            case BYTE:
            case INTEGER:
            case DECIMAL:
                return "IFNULL(CAST(" + expr + " AS STRING), '')";
            case DATE:
                return "IFNULL(DATE_FORMAT(" + expr + ", '%Y-%m-%d'), '')";
            case DATETIME:
                return "IFNULL(DATE_FORMAT(" + expr + ", '%Y-%m-%d %H:%i:%s'), '')";
            default:
                throw new IllegalStateException("ValueKind không xác định: " + c.kind());
        }
    }
}
