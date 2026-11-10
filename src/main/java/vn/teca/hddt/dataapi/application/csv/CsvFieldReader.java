package vn.teca.hddt.dataapi.application.csv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVRecord;

import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.csv.CsvColumn;
import vn.teca.hddt.dataapi.domain.csv.CsvLayout;

/**
 * Đọc và kiểm tra từng ô theo bố cục: số cột đúng, trường bắt buộc, kiểu (Byte/Integer/Date/Datetime/BigDecimal),
 * độ dài theo byte UTF-8 (Doris VARCHAR tính byte). Lỗi được gom, không ném giữa chừng.
 */
public final class CsvFieldReader {
    private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,15}(\\.\\d{1,6})?");
    private static final Pattern INTEGER = Pattern.compile("\\d{1,10}");
    private static final Pattern BYTE = Pattern.compile("\\d{1,3}");

    private final CsvLayout layout;
    private final String objectKey;
    private final CSVRecord record;
    private final List<RowError> errors = new ArrayList<>();

    public CsvFieldReader(CsvLayout layout, String objectKey, CSVRecord record) {
        this.layout = layout;
        this.objectKey = objectKey;
        this.record = record;
        if (record.size() != layout.size()) {
            errors.add(new RowError(objectKey, record.getRecordNumber(), null,
                    "số cột " + record.size() + " khác " + layout.size()));
        }
    }

    public boolean columnCountOk() {
        return record.size() == layout.size();
    }

    public List<RowError> errors() {
        return errors;
    }

    private String raw(String name) {
        CsvColumn c = layout.column(name);
        String v = record.get(c.index() - 1);
        if (v.isEmpty()) {
            if (c.required()) {
                error(name, "bắt buộc nhưng rỗng");
            }
            return null;
        }
        return v;
    }

    private void error(String name, String reason) {
        errors.add(new RowError(objectKey, record.getRecordNumber(), name, reason));
    }

    public String string(String name, int maxUtf8Bytes) {
        String v = raw(name);
        if (v != null && v.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) {
            error(name, "vượt " + maxUtf8Bytes + " byte");
            return null;
        }
        return v;
    }

    public Integer byteValue(String name) {
        String v = raw(name);
        if (v == null) {
            return null;
        }
        if (!BYTE.matcher(v).matches() || Integer.parseInt(v) > 127) {
            error(name, "không phải số nguyên 0..127");
            return null;
        }
        return Integer.valueOf(v);
    }

    public Long integer(String name) {
        String v = raw(name);
        if (v == null) {
            return null;
        }
        if (!INTEGER.matcher(v).matches() || Long.parseLong(v) > Integer.MAX_VALUE) {
            error(name, "không phải số nguyên không âm");
            return null;
        }
        return Long.valueOf(v);
    }

    public BigDecimal decimal(String name) {
        String v = raw(name);
        if (v == null) {
            return null;
        }
        if (!DECIMAL.matcher(v).matches()) {
            error(name, "không phải số thập phân (tối đa 15 chữ số nguyên, 6 chữ số thập phân, không dạng mũ)");
            return null;
        }
        return new BigDecimal(v);
    }

    public LocalDate date(String name) {
        String v = raw(name);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v, DateFormats.DATE);
        } catch (DateTimeParseException e) {
            error(name, "không đúng định dạng yyyy-MM-dd");
            return null;
        }
    }

    public LocalDateTime dateTime(String name) {
        String v = raw(name);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, DateFormats.DATETIME);
        } catch (DateTimeParseException e) {
            error(name, "không đúng định dạng yyyy-MM-dd HH:mm:ss");
            return null;
        }
    }

    public String rawOptional(String name) {
        return raw(name);
    }

    public void fail(String name, String reason) {
        error(name, reason);
    }
}
