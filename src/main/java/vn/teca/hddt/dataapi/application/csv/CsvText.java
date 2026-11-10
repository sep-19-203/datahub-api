package vn.teca.hddt.dataapi.application.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import vn.teca.hddt.dataapi.domain.common.DateFormats;

/**
 * Quy tắc văn bản CSV dùng chung (SI-35), phải khớp tuyệt đối với biểu thức SQL trong {@code CsvLineSql}:
 * chuỗi luôn bọc nháy kép (nhân đôi nháy kép bên trong) để giữ số 0 đầu MST và an toàn với dấu phẩy/xuống dòng;
 * số dạng thập phân thường (không mũ); ngày yyyy-MM-dd; ngày giờ yyyy-MM-dd HH:mm:ss; NULL ⇒ rỗng; phân cách ','.
 */
public final class CsvText {
    private CsvText() {
    }

    public static String str(String s) {
        return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static String num(BigDecimal d) {
        return d == null ? "" : d.toPlainString();
    }

    public static String num(Number n) {
        if (n == null) {
            return "";
        }
        return n instanceof BigDecimal ? ((BigDecimal) n).toPlainString() : n.toString();
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DateFormats.format(d);
    }

    public static String dateTime(LocalDateTime d) {
        return d == null ? "" : DateFormats.format(d);
    }
}
