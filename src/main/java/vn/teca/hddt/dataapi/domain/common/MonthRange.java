package vn.teca.hddt.dataapi.domain.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Khoảng tháng [tthang, dthang] dạng MM/YYYY, gồm cả tháng cuối (HD1!AA15/AA16, HD5, HD7).
 * {@link #firstDay()} / {@link #lastPeriod()} khớp cột KY (= ngày đầu tháng) của HD_MST_LIST / HD_MST_SUMMARY.
 */
public final class MonthRange {
    private final YearMonth from;
    private final YearMonth to;

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public MonthRange(YearMonth from, YearMonth to) {
        if (from == null || to == null) {
            throw new DomainValidationException("tthang", "tthang/dthang bắt buộc");
        }
        if (from.isAfter(to)) {
            throw new DomainValidationException("tthang", "tthang phải nhỏ hơn hoặc bằng dthang");
        }
        this.from = from;
        this.to = to;
    }

    public YearMonth from() {
        return from;
    }

    public YearMonth to() {
        return to;
    }

    public static MonthRange parse(String tthang, String dthang, int maxMonths) {
        MonthRange r = new MonthRange(parseMonth("tthang", tthang), parseMonth("dthang", dthang));
        if (r.months() > maxMonths) {
            throw new DomainValidationException("dthang", "Khoảng tháng vượt quá " + maxMonths + " tháng");
        }
        return r;
    }

    static YearMonth parseMonth(String field, String value) {
        if (value == null || !value.matches("(0[1-9]|1[0-2])/\\d{4}")) {
            throw new DomainValidationException(field, field + " phải có dạng MM/YYYY");
        }
        try {
            return YearMonth.parse(value, MM_YYYY);
        } catch (DateTimeParseException e) {
            throw new DomainValidationException(field, field + " phải có dạng MM/YYYY");
        }
    }

    public long months() {
        return ChronoUnit.MONTHS.between(from, to) + 1;
    }

    /** KY nhỏ nhất (ngày đầu tháng tthang). */
    public LocalDate firstDay() {
        return from.atDay(1);
    }

    /** KY lớn nhất (ngày đầu tháng dthang) — so sánh {@code KY <= lastPeriod()} vì KY luôn là ngày đầu tháng. */
    public LocalDate lastPeriod() {
        return to.atDay(1);
    }

    /** Ngày đầu tháng sau dthang (cận trên loại trừ cho cột ngày thường). */
    public LocalDate endExclusive() {
        return to.plusMonths(1).atDay(1);
    }

    public String format() {
        return MM_YYYY.format(from) + "-" + MM_YYYY.format(to);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonthRange)) {
            return false;
        }
        MonthRange other = (MonthRange) o;
        return Objects.equals(from, other.from)
                && Objects.equals(to, other.to);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(from);
        result = 31 * result + Objects.hashCode(to);
        return result;
    }

    @Override
    public String toString() {
        return "MonthRange[from=" + from
                + ", to=" + to + "]";
    }
}
