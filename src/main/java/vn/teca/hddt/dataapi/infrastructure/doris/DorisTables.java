package vn.teca.hddt.dataapi.infrastructure.doris;

import java.util.regex.Pattern;

/** Tên bảng đích đã duyệt (DDL v4 — hddt-spark-backfill/sql/00_target_tables_v4.sql). Tên DB chỉ từ cấu hình, đã kiểm. */
public final class DorisTables {
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private final String db;

    public DorisTables(String database) {
        if (database == null || !IDENT.matcher(database).matches()) {
            throw new IllegalArgumentException("Tên database Doris không hợp lệ");
        }
        this.db = database;
    }

    private String t(String name) {
        return "`" + db + "`.`" + name + "`";
    }

    public String hdr() {
        return t("HOADON_HDR");
    }

    public String dtl() {
        return t("HOADON_DTL");
    }

    public String list() {
        return t("HD_MST_LIST");
    }

    public String summary() {
        return t("HD_MST_SUMMARY");
    }

    /** Điều kiện nối danh sách theo MST với header theo khóa UNIQUE của HOADON_HDR. */
    public static final String LIST_HDR_JOIN =
            "h.TDLAP = l.TDLAP AND h.CQT = l.CQT AND h.LOAI_HD = l.LOAI_HD AND h.ID = l.ID";

    public static String placeholders(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Danh sách IN rỗng");
        }
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
