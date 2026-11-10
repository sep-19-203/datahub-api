package vn.teca.hddt.dataapi.infrastructure.doris;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.port.SummaryQueryPort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.config.HddtProperties.Hd7.PeriodBasis;
import vn.teca.hddt.dataapi.config.HddtProperties.Hd7.SingleRateSource;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.hd7.TaxRateCode;
import vn.teca.hddt.dataapi.domain.invoice.Direction;

/**
 * HD7 trên Doris — cả mua vào ([23]/[24]) và bán ra gom theo mã thuế suất trên cùng một phạm vi HĐ:
 * HD_MST_LIST (CHIEU, IS_ELIGIBLE=1 — C-17) ⨝ HOADON_HDR, + R1 mẫu số nếu bật (GiaiPhap v3.8 Bảng 5).
 * <p>Kỳ ({@code hddt.hd7.period-basis}):
 * <ul>
 * <li>NGAY_LAP (mặc định, v3.8 R3/R4, 05/11): ngày tính kỳ = TDLHDGOC nếu HĐ có HĐ gốc (SHDGOC) và có ngày lập gốc,
 * còn lại TDLAP — cho cả bán ra và mua vào. HĐ thay thế/điều chỉnh lập ở tháng sau vẫn về kỳ của HĐ gốc, nên danh
 * sách quét HĐ có TDLAP ≥ đầu kỳ: trong kỳ thì mọi HĐ, sau kỳ chỉ HĐ có TCHAT ≠ 1 (thay thế/điều chỉnh).
 * Chỉ dùng ngày lập HĐ gốc trực tiếp (cấp 1) — v3.8 lần chuỗi tới HĐ gốc đầu tiên ≤ 5 cấp ở tầng nạp (Q8 chưa chốt).</li>
 * <li>KY_DANH_SACH: cột KY của HD_MST_LIST (bán ra tháng TDLAP; mua vào tháng NKY→NCMA→TDLAP như HD1, SI-42).</li>
 * </ul>
 * Cắt partition HOADON_HDR/DTL theo MIN/MAX TDLAP của danh sách (trừ bán ra KY_DANH_SACH: tháng của kỳ).
 * <p>Tiền theo thuế suất (R6), quy đổi VND theo R7 nếu bật ({@code currency-conversion-enabled}):
 * (1) HĐ có THTTLTSUAT: tách từng phần tử {tsuat, thtien, tthue};
 * (2) HĐ thiếu THTTLTSUAT: dòng hàng HOADON_DTL theo LTSUAT (bỏ dòng ghi chú TCHAT=4, chiết khấu TCHAT=3 trừ đi);
 * số tiền = tổng dòng hàng theo thuế suất; HĐ chỉ 1 thuế suất mà dòng không có số thì lấy header (v3.8, {@code
 * single-rate-source: LINES}; HEADER = ưu tiên header như 01/11–05/11);
 * (3) HĐ không có dòng hàng nào có thuế suất ⇒ suy thuế suất từ tỷ lệ TGTTHUE/TGTCTHUE header ({@code
 * hddt.hd7.header-rate-inference}); không suy được ⇒ mã không xác định (bỏ qua + cảnh báo ở tầng ứng dụng).
 * D3 (v3.8): HĐ điều chỉnh (TCHAT = 3) thiếu THTTLTSUAT đóng góp 0 nếu bật ({@code adjustment-without-rates-zero}).
 * KHÔNG dùng cột PCT* (ETL gộp 8% MTT vào KHAC, mặc định 10% khi thiếu thuế suất) và KHÔNG dùng HD_MST_SUMMARY.
 * Số trong THTTLTSUAT (VARIANT/JSON) là double ⇒ chính xác tới ~15 chữ số có nghĩa.
 */
@Component
public class DorisSummaryAdapter implements SummaryQueryPort {
    /** Ngày tính kỳ v3.8 R3/R4: HĐ có HĐ gốc ⇒ ngày lập HĐ gốc; không có ngày gốc ⇒ chính nó. */
    static final String PERIOD_DATE = "(CASE WHEN h.SHDGOC IS NOT NULL AND h.TDLHDGOC IS NOT NULL THEN h.TDLHDGOC"
            + " ELSE h.TDLAP END)";
    /** R7 v3.8: hệ số quy đổi VND. */
    static final String FX_RATE = "(CASE WHEN UPPER(IFNULL(h.DVTTE, 'VND')) <> 'VND' AND h.TGIA > 0 THEN h.TGIA"
            + " ELSE 1 END)";
    /** D3: HĐ điều chỉnh. */
    private static final String ADJUSTMENT = "h.TCHAT = 3";

    private final JdbcTemplate jdbc;
    private final DorisTables t;
    private final boolean formRule;
    private final String inferredRate;
    private final PeriodBasis basis;
    private final String fx;
    private final boolean adjustmentZero;
    private final SingleRateSource singleRateSource;

    public DorisSummaryAdapter(@Qualifier("dorisSyncJdbc") JdbcTemplate jdbc, HddtProperties props) {
        HddtProperties.Hd7 c = props.hd7();
        this.jdbc = jdbc;
        this.t = new DorisTables(props.doris().database());
        this.formRule = c.formRuleEnabled();
        this.inferredRate = inferredRateExpr(c.headerRateInference(), c.headerRateToleranceVnd(),
                c.headerRateToleranceRatio());
        this.basis = c.periodBasis() == null ? PeriodBasis.NGAY_LAP : c.periodBasis();
        this.fx = c.currencyConversionEnabled() ? FX_RATE : "1";
        this.adjustmentZero = c.adjustmentWithoutRatesZero();
        this.singleRateSource = c.singleRateSource() == null ? SingleRateSource.LINES : c.singleRateSource();
    }

    @Override
    public RateBreakdown breakdown(Direction direction, String mst, MonthRange range) {
        RateBreakdown.Builder b = RateBreakdown.builder();
        Object[] listArgs = listArgs(direction, mst, range);
        LocalDate from;
        LocalDate toExclusive;
        if (direction == Direction.BAN_RA && basis == PeriodBasis.KY_DANH_SACH) {
            from = range.firstDay();
            toExclusive = range.endExclusive();
        } else {
            LocalDate[] bounds = jdbc.queryForObject(boundsSql(direction), (rs, i) -> new LocalDate[]{
                    rs.getObject("MIN_TDLAP", LocalDate.class), rs.getObject("MAX_TDLAP", LocalDate.class)}, listArgs);
            if (bounds == null || bounds[0] == null) {
                return b.build();
            }
            from = bounds[0];
            toExclusive = bounds[1].plusDays(1);
        }
        Object[] scopeArgs = scopeArgs(listArgs, from, toExclusive, range);
        jdbc.query(headerRatesSql(direction), rs -> {
            b.add(rs.getString("TS"), rs.getBigDecimal("THTIEN"), rs.getBigDecimal("TTHUE"));
        }, scopeArgs);
        Map<String, Totals> matched = new HashMap<>();
        jdbc.query(lineRatesSql(direction), rs -> {
            b.add(rs.getString("TS"), rs.getBigDecimal("THTIEN"), rs.getBigDecimal("TTHUE"));
            matched.computeIfAbsent(rs.getString("INF"), k -> new Totals())
                    .add(rs.getLong("NHD"), rs.getBigDecimal("HTIEN_HD"), rs.getBigDecimal("HTHUE_HD"));
        }, concat(new Object[]{from, toExclusive}, scopeArgs));
        long[] count = {0};
        Map<String, Totals> missing = new HashMap<>();
        jdbc.query(headerGroupsSql(direction), rs -> {
            count[0] += rs.getLong("N");
            if (rs.getInt("HAS_TS") == 0) {
                missing.computeIfAbsent(rs.getString("INF"), k -> new Totals())
                        .add(rs.getLong("N"), rs.getBigDecimal("HTIEN"), rs.getBigDecimal("HTHUE"));
            }
        }, scopeArgs);
        // HĐ thiếu THTTLTSUAT mà không khớp được dòng hàng có thuế suất ⇒ số liệu header theo mức suy được (hoặc không xác định)
        missing.forEach((inf, all) -> {
            Totals m = matched.getOrDefault(inf, new Totals());
            if (all.n > m.n) {
                b.add(inf, all.amount.subtract(m.amount), all.tax.subtract(m.tax));
            }
        });
        return b.invoiceCount(count[0]).build();
    }

    /** Cộng dồn số HĐ và tổng header theo nhóm thuế suất suy được. */
    private static final class Totals {
        long n;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        void add(long count, BigDecimal a, BigDecimal x) {
            n += count;
            amount = amount.add(zero(a));
            tax = tax.add(zero(x));
        }
    }

    /**
     * Điều kiện trên HD_MST_LIST. KY_DANH_SACH: (KY từ, KY đến, MST). NGAY_LAP: (MST, đầu kỳ, hết kỳ[, đầu kỳ cho KY
     * bán ra]) — HĐ sau kỳ chỉ lấy thay thế/điều chỉnh (TCHAT ≠ 1) vì chỉ chúng có thể về kỳ của HĐ gốc.
     */
    String listFilter(Direction d) {
        String common = " AND l.CHIEU = '" + d.name() + "' AND l.IS_ELIGIBLE = 1";
        if (basis == PeriodBasis.KY_DANH_SACH) {
            return "l.KY >= ? AND l.KY <= ? AND l.MST = ?" + common;
        }
        return "l.MST = ?" + common + " AND l.TDLAP >= ? AND (l.TDLAP < ? OR l.TCHAT IS NULL OR l.TCHAT <> 1)"
                // bán ra: KY = tháng TDLAP ⇒ cắt partition danh sách; mua vào KY theo NKY nên không dùng được
                + (d == Direction.BAN_RA ? " AND l.KY >= ?" : "");
    }

    private Object[] listArgs(Direction d, String mst, MonthRange range) {
        if (basis == PeriodBasis.KY_DANH_SACH) {
            return new Object[]{range.firstDay(), range.lastPeriod(), mst};
        }
        return d == Direction.BAN_RA
                ? new Object[]{mst, range.firstDay(), range.endExclusive(), range.firstDay()}
                : new Object[]{mst, range.firstDay(), range.endExclusive()};
    }

    private Object[] scopeArgs(Object[] listArgs, LocalDate from, LocalDate toExclusive, MonthRange range) {
        Object[] tdlap = {from, toExclusive};
        return basis == PeriodBasis.KY_DANH_SACH ? concat(listArgs, tdlap)
                : concat(concat(listArgs, tdlap), new Object[]{range.firstDay(), range.endExclusive()});
    }

    private static Object[] concat(Object[] a, Object[] b) {
        List<Object> all = new ArrayList<>(Arrays.asList(a));
        all.addAll(Arrays.asList(b));
        return all.toArray();
    }

    /** MIN/MAX TDLAP của danh sách (1 bucket HD_MST_LIST) để cắt partition HOADON_HDR/DTL. */
    String boundsSql(Direction d) {
        return "SELECT MIN(l.TDLAP) AS MIN_TDLAP, MAX(l.TDLAP) AS MAX_TDLAP FROM " + t.list() + " l WHERE "
                + listFilter(d);
    }

    /** Phạm vi HĐ hợp lệ của MST trong kỳ (tham số: {@link #scopeArgs}). */
    private String scope(Direction d) {
        return t.list() + " l JOIN " + t.hdr() + " h ON " + DorisTables.LIST_HDR_JOIN + " WHERE " + scopeFilter(d);
    }

    private String scopeFilter(Direction d) {
        return listFilter(d) + " AND h.TDLAP >= ? AND h.TDLAP < ?"
                + (basis == PeriodBasis.NGAY_LAP ? " AND " + PERIOD_DATE + " >= ? AND " + PERIOD_DATE + " < ?" : "")
                + (formRule ? " AND " + ExportSqlBuilder.FORM_RULE_R1 : "");
    }

    /**
     * HĐ "có THTTLTSUAT" ⇔ chuỗi JSON là mảng chứa object; còn lại (SQL NULL, JSON null, '[]', không phải mảng) là
     * "thiếu". Hai điều kiện bù nhau tuyệt đối để mỗi HĐ vào đúng một nhánh — VARIANT chứa JSON null có
     * {@code IS NULL} = false nhưng CAST AS STRING = NULL (gặp trên DEV 01/11), nên không dùng IS NULL / IN (...).
     */
    private static final String RATES_JSON = "COALESCE(TRIM(CAST(h.THTTLTSUAT AS STRING)), '')";
    private static final String HAS_RATES_EXPR = RATES_JSON + " LIKE '[%{%'";
    private static final String HAS_RATES = " AND " + HAS_RATES_EXPR;
    private static final String NO_RATES = " AND NOT (" + HAS_RATES_EXPR + ")";

    /**
     * Mức thuế suất suy từ header: |TGTTHUE − TGTCTHUE × r| ≤ max(VND, |TGTCTHUE| × ratio). Chỉ mức dương (0% không
     * phân biệt được với KCT/KKKNT); hằng số lấy từ cấu hình đã kiểm tra là số, không phải input người dùng.
     */
    static String inferredRateExpr(List<String> codes, BigDecimal tolVnd, BigDecimal tolRatio) {
        if (codes == null || codes.isEmpty()) {
            return "CAST(NULL AS STRING)";
        }
        StringBuilder sb = new StringBuilder("CASE WHEN h.TGTCTHUE IS NULL OR h.TGTCTHUE = 0 OR h.TGTTHUE IS NULL"
                + " OR h.TGTTHUE = 0 THEN NULL");
        for (String c : codes) {
            String n = TaxRateCode.normalize(c);
            if (!n.matches("\\d+(\\.\\d+)?%")) {
                throw new IllegalArgumentException("hddt.hd7.header-rate-inference chỉ nhận mức % dạng số: " + c);
            }
            BigDecimal r = new BigDecimal(n.substring(0, n.length() - 1)).movePointLeft(2);
            if (r.signum() <= 0) {
                throw new IllegalArgumentException("hddt.hd7.header-rate-inference không suy 0% (trùng KCT/KKKNT): " + c);
            }
            sb.append(" WHEN ABS(h.TGTTHUE - h.TGTCTHUE * ").append(r.toPlainString()).append(") <= GREATEST(")
                    .append(tolVnd.toPlainString()).append(", ABS(h.TGTCTHUE) * ").append(tolRatio.toPlainString())
                    .append(") THEN '").append(n).append("'");
        }
        return sb.append(" ELSE NULL END").toString();
    }

    /** (1) Tách THTTLTSUAT của HĐ có mảng thuế suất ({@link #HAS_RATES}), quy đổi VND theo R7. */
    String headerRatesSql(Direction d) {
        String ts = "LOWER(TRIM(get_json_string(e, '$.tsuat')))";
        return "SELECT " + ts + " AS TS,"
                + " SUM(CAST(get_json_string(e, '$.thtien') AS DECIMAL(27,6)) * s.FX) AS THTIEN,"
                + " SUM(CAST(get_json_string(e, '$.tthue') AS DECIMAL(27,6)) * s.FX) AS TTHUE"
                + " FROM (SELECT CAST(h.THTTLTSUAT AS STRING) AS J, " + fx + " AS FX FROM " + scope(d) + HAS_RATES
                + ") s LATERAL VIEW explode_json_array_json(s.J) x AS e GROUP BY " + ts;
    }

    /** D3: HĐ điều chỉnh thiếu THTTLTSUAT không lấy dòng hàng/header. */
    private String notZeroAdjustment() {
        return adjustmentZero ? " AND (h.TCHAT IS NULL OR h.TCHAT <> 3)" : "";
    }

    /**
     * (2) HĐ thiếu THTTLTSUAT ⇒ dòng hàng (tham số DTL TDLAP từ/trước đặt trước tham số phạm vi). INNER JOIN để tập HĐ
     * của MST (nhỏ) làm phía build, DTL nhận runtime filter theo IDHDON — LEFT JOIN sẽ dựng bảng băm từ toàn bộ DTL
     * của khoảng ngày. NHD/HTIEN_HD/HTHUE_HD theo nhóm INF: số HĐ khớp được dòng hàng và tổng header của chúng (để
     * tính phần còn lại ở {@link #headerGroupsSql}).
     */
    String lineRatesSql(Direction d) {
        String lineTax = "CAST(d.TTHUE AS DECIMAL(27,6))";
        String perInvoice = "SELECT /*+ leading(d {h l}) */ h.TDLAP, h.CQT, h.LOAI_HD, h.ID, LOWER(TRIM(d.LTSUAT)) AS TS,"
                + " SUM(" + discounted("d.THTIEN") + " * " + fx + ") AS LTIEN,"
                + " SUM(" + discounted(lineTax) + " * " + fx + ") AS LTHUE,"
                + " MAX(h.TGTCTHUE * " + fx + ") AS HTIEN, MAX(h.TGTTHUE * " + fx + ") AS HTHUE,"
                + " MAX(" + inferredRate + ") AS INF"
                + " FROM " + t.list() + " l JOIN " + t.hdr() + " h ON " + DorisTables.LIST_HDR_JOIN
                + " JOIN " + t.dtl() + " d ON d.TDLAP = h.TDLAP AND d.CQT = h.CQT AND d.LOAI_HD = h.LOAI_HD"
                + " AND d.IDHDON = h.ID"
                + " WHERE d.TDLAP >= ? AND d.TDLAP < ? AND (d.TCHAT IS NULL OR d.TCHAT <> 4)"
                + " AND d.LTSUAT IS NOT NULL AND TRIM(d.LTSUAT) <> '' AND " + scopeFilter(d) + NO_RATES
                + notZeroAdjustment()
                + " GROUP BY h.TDLAP, h.CQT, h.LOAI_HD, h.ID, LOWER(TRIM(d.LTSUAT))";
        String inv = "PARTITION BY g.TDLAP, g.CQT, g.LOAI_HD, g.ID";
        return "SELECT TS, INF, SUM(" + singleRate("HTIEN", "LTIEN") + ") AS THTIEN,"
                + " SUM(" + singleRate("HTHUE", "LTHUE") + ") AS TTHUE,"
                + " SUM(CASE WHEN RN = 1 THEN 1 ELSE 0 END) AS NHD,"
                + " SUM(CASE WHEN RN = 1 THEN HTIEN END) AS HTIEN_HD, SUM(CASE WHEN RN = 1 THEN HTHUE END) AS HTHUE_HD"
                + " FROM (SELECT g.TS, g.INF, g.LTIEN, g.LTHUE, g.HTIEN, g.HTHUE,"
                + " COUNT(*) OVER (" + inv + ") AS NR, ROW_NUMBER() OVER (" + inv + " ORDER BY g.TS) AS RN"
                + " FROM (" + perInvoice + ") g) z GROUP BY TS, INF";
    }

    /**
     * Số tiền của một thuế suất dòng hàng (NR = số thuế suất của HĐ). LINES (v3.8 `v_stg_ts`): tổng dòng hàng, dòng không
     * có số ⇒ header khi HĐ chỉ 1 thuế suất. HEADER: HĐ 1 thuế suất lấy header, header rỗng ⇒ dòng hàng.
     */
    private String singleRate(String header, String lines) {
        return singleRateSource == SingleRateSource.HEADER
                ? "CASE WHEN NR = 1 THEN COALESCE(" + header + ", " + lines + ") ELSE " + lines + " END"
                : "COALESCE(" + lines + ", CASE WHEN NR = 1 THEN " + header + " END)";
    }

    /**
     * (3) Mọi HĐ trong phạm vi theo (nhóm, mức suy từ header): tổng số HĐ (= slmvao) và tổng header (VND) của HĐ thiếu
     * THTTLTSUAT. HAS_TS: 1 = có THTTLTSUAT, 2 = HĐ điều chỉnh thiếu THTTLTSUAT đóng góp 0 (D3), 0 = thiếu. Không chạm DTL.
     */
    String headerGroupsSql(Direction d) {
        return "SELECT CASE WHEN " + HAS_RATES_EXPR + " THEN 1"
                + (adjustmentZero ? " WHEN " + ADJUSTMENT + " THEN 2" : "") + " ELSE 0 END AS HAS_TS, "
                + inferredRate + " AS INF, COUNT(*) AS N, SUM(h.TGTCTHUE * " + fx + ") AS HTIEN,"
                + " SUM(h.TGTTHUE * " + fx + ") AS HTHUE FROM " + scope(d) + " GROUP BY 1, 2";
    }

    /** Dòng chiết khấu thương mại (TCHAT=3) làm giảm doanh thu bất kể dấu nguồn; dòng khác giữ nguyên dấu (HĐ điều chỉnh giảm). */
    private static String discounted(String expr) {
        return "(CASE WHEN d.TCHAT = 3 THEN -ABS(" + expr + ") ELSE " + expr + " END)";
    }

    /** SUM trên tập rỗng trả NULL ⇒ 0 (không có hóa đơn), không phải giá trị bịa. */
    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
