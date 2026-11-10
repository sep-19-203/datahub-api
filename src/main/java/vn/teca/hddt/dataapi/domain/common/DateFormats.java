package vn.teca.hddt.dataapi.domain.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Định dạng ngày giờ thống nhất cho JSON và CSV (SI-29, SI-35). Múi giờ nghiệp vụ Asia/Ho_Chi_Minh. */
public final class DateFormats {
    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private DateFormats() {
    }

    public static LocalDate parseDate(String field, String value) {
        if (value == null || value.isEmpty()) {
            throw new DomainValidationException(field, field + " bắt buộc");
        }
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            throw new DomainValidationException(field, field + " phải có dạng yyyy-MM-dd");
        }
    }

    public static LocalDateTime parseDateTime(String field, String value) {
        try {
            return LocalDateTime.parse(value, DATETIME);
        } catch (DateTimeParseException e) {
            throw new DomainValidationException(field, field + " phải có dạng yyyy-MM-dd HH:mm:ss");
        }
    }

    public static String format(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    public static String format(LocalDateTime d) {
        return d == null ? null : DATETIME.format(d);
    }
}
