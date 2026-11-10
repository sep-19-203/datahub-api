package vn.teca.hddt.dataapi.infrastructure.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.config.HddtProperties.Hd7.PeriodBasis;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy.UnassignedCodePolicy;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.support.TestProps;

/** Cấu trúc SQL HD7 (mua vào + bán ra) — bản 01/11 đã EXPLAIN trên Doris DEV 4.0.8 (TEST_RESULTS 4d); 05/11 theo v3.8. */
class SummarySqlTest {
    private final DorisSummaryAdapter a = new DorisSummaryAdapter(null, TestProps.defaults());

    private static DorisSummaryAdapter adapter(boolean formRule, PeriodBasis basis, boolean fx, boolean adjZero) {
        return new DorisSummaryAdapter(null, TestProps.withHd7(TestProps.hd7(TestProps.fullMapping(),
                UnassignedCodePolicy.IGNORE, formRule, basis, fx, adjZero)));
    }

    static long params(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }

    @Test
    void headerRates_explodeThttltsuat_readNumbersAsText_notDoubleCast() {
        for (Direction d : Direction.values()) {
            String sql = a.headerRatesSql(d);
            assertThat(sql).contains("explode_json_array_json").contains("CAST(get_json_string(e, '$.thtien') AS DECIMAL(27,6))")
                    .doesNotContain("json_extract(").doesNotContain("PCT")
                    .contains("AND COALESCE(TRIM(CAST(h.THTTLTSUAT AS STRING)), '') LIKE '[%{%'")
                    .contains("l.CHIEU = '" + d.name() + "'");
        }
        // NGAY_LAP: bán ra có thêm KY ≥ đầu kỳ để cắt partition danh sách
        assertThat(params(a.headerRatesSql(Direction.BAN_RA))).isEqualTo(8);
        assertThat(params(a.headerRatesSql(Direction.MUA_VAO))).isEqualTo(7);
    }

    @Test
    void lineRates_innerJoinDtl_withInvoiceSetAsBuildSide_neverLeftJoin() {
        String sql = a.lineRatesSql(Direction.BAN_RA);
        assertThat(sql).contains("/*+ leading(d {h l}) */").contains("JOIN `datahub_hddt`.`HOADON_DTL` d")
                .doesNotContain("LEFT JOIN").contains("d.TCHAT <> 4").contains("WHEN d.TCHAT = 3 THEN -ABS(");
        assertThat(params(sql)).isEqualTo(10);
    }

    @Test
    void lineRates_singleRateInvoice_usesLineSums_headerOnlyWhenLinesHaveNoAmount() {
        // SI-62 (v3.8 R6, v_stg_ts): tổng dòng hàng; HĐ 1 thuế suất mà dòng không có số ⇒ header (DEV: thuế dòng null)
        assertThat(a.lineRatesSql(Direction.BAN_RA)).contains("SUM(COALESCE(LTIEN, CASE WHEN NR = 1 THEN HTIEN END)) AS THTIEN")
                .contains("SUM(COALESCE(LTHUE, CASE WHEN NR = 1 THEN HTHUE END)) AS TTHUE").contains("GROUP BY TS, INF");
    }

    @Test
    void lineRates_headerMode_keepsHeaderForSingleRate_andLinesWhenHeaderIsNull() {
        HddtProperties.Hd7 h = new HddtProperties.Hd7(12, true, Jdk8Compat.listOf("0%", "5%", "10%"), TestProps.fullMapping(),
                UnassignedCodePolicy.IGNORE, Jdk8Compat.listOf("10%"), BigDecimal.ONE, new BigDecimal("0.0001"),
                PeriodBasis.NGAY_LAP, true, true, HddtProperties.Hd7.SingleRateSource.HEADER);
        assertThat(new DorisSummaryAdapter(null, TestProps.withHd7(h)).lineRatesSql(Direction.BAN_RA))
                .contains("WHEN NR = 1 THEN COALESCE(HTIEN, LTIEN)").contains("WHEN NR = 1 THEN COALESCE(HTHUE, LTHUE)");
    }

    @Test
    void hasAndMissingRateFilters_areExactComplements_soNoInvoiceIsDropped() {
        // VARIANT chứa JSON null: IS NULL = false nhưng CAST AS STRING = NULL ⇒ điều kiện cũ (IS NULL / IN) bỏ sót HĐ (DEV 01/11)
        String has = "COALESCE(TRIM(CAST(h.THTTLTSUAT AS STRING)), '') LIKE '[%{%'";
        assertThat(a.headerRatesSql(Direction.BAN_RA)).contains(" AND " + has);
        assertThat(a.lineRatesSql(Direction.BAN_RA)).contains(" AND NOT (" + has + ")");
        assertThat(a.headerGroupsSql(Direction.BAN_RA)).contains("CASE WHEN " + has + " THEN 1 WHEN h.TCHAT = 3 THEN 2 ELSE 0 END AS HAS_TS");
    }

    @Test
    void headerGroups_countAllInvoices_withoutTouchingDtl() {
        String sql = a.headerGroupsSql(Direction.MUA_VAO);
        assertThat(sql).doesNotContain("HOADON_DTL").contains("COUNT(*) AS N").contains("GROUP BY 1, 2");
        assertThat(params(sql)).isEqualTo(7);
    }

    @Test
    void formRuleR1_appliesToBothDirectionsAndEveryQuery_andCanBeDisabled() {
        // SI-54: cùng biểu thức với HD1/HD5
        for (Direction d : Direction.values()) {
            assertThat(a.headerRatesSql(d)).contains(ExportSqlBuilder.FORM_RULE_R1);
            assertThat(a.lineRatesSql(d)).contains(ExportSqlBuilder.FORM_RULE_R1);
            assertThat(a.headerGroupsSql(d)).contains(ExportSqlBuilder.FORM_RULE_R1);
        }
        assertThat(adapter(false, PeriodBasis.NGAY_LAP, true, true).headerGroupsSql(Direction.BAN_RA)).doesNotContain("KHMSHDON");
    }

    @Test
    void periodNgayLap_usesOriginalInvoiceDate_andScansLaterReplacementsOnly() {
        // SI-59 (v3.8 R3/R4): kỳ = ngày lập HĐ gốc nếu có, cả hai chiều; HĐ sau kỳ chỉ lấy thay thế/điều chỉnh
        for (Direction d : Direction.values()) {
            assertThat(a.headerGroupsSql(d)).contains(DorisSummaryAdapter.PERIOD_DATE + " >= ? AND "
                    + DorisSummaryAdapter.PERIOD_DATE + " < ?").doesNotContain("l.KY <= ?")
                    .contains("l.TDLAP >= ? AND (l.TDLAP < ? OR l.TCHAT IS NULL OR l.TCHAT <> 1)");
            assertThat(a.boundsSql(d)).contains("MIN(l.TDLAP)").doesNotContain("HOADON_HDR").doesNotContain("h.");
        }
        assertThat(DorisSummaryAdapter.PERIOD_DATE).contains("h.SHDGOC IS NOT NULL AND h.TDLHDGOC IS NOT NULL THEN h.TDLHDGOC");
        assertThat(a.boundsSql(Direction.BAN_RA)).contains("l.KY >= ?");
        assertThat(a.boundsSql(Direction.MUA_VAO)).doesNotContain("l.KY");
    }

    @Test
    void periodKyDanhSach_keepsListPeriod_likeHd1() {
        DorisSummaryAdapter k = adapter(true, PeriodBasis.KY_DANH_SACH, true, true);
        assertThat(k.headerGroupsSql(Direction.MUA_VAO)).contains("l.KY >= ? AND l.KY <= ? AND l.MST = ?")
                .doesNotContain("TDLHDGOC").contains("h.TDLAP >= ? AND h.TDLAP < ?");
        assertThat(params(k.headerGroupsSql(Direction.MUA_VAO))).isEqualTo(5);
        assertThat(params(k.lineRatesSql(Direction.BAN_RA))).isEqualTo(7);
        assertThat(k.boundsSql(Direction.MUA_VAO)).contains("l.KY >= ? AND l.KY <= ?");
    }

    @Test
    void currencyConversion_multipliesEveryAmountByRate_andCanBeDisabled() {
        // SI-60 (v3.8 R7)
        String fx = DorisSummaryAdapter.FX_RATE;
        assertThat(fx).contains("UPPER(IFNULL(h.DVTTE, 'VND')) <> 'VND' AND h.TGIA > 0 THEN h.TGIA ELSE 1");
        assertThat(a.headerRatesSql(Direction.BAN_RA)).contains(fx + " AS FX").contains("AS DECIMAL(27,6)) * s.FX");
        assertThat(a.lineRatesSql(Direction.BAN_RA)).contains("MAX(h.TGTCTHUE * " + fx + ")").contains("END) * " + fx + ")");
        assertThat(a.headerGroupsSql(Direction.BAN_RA)).contains("SUM(h.TGTCTHUE * " + fx + ")");
        // suy thuế suất dùng tỷ lệ, không phụ thuộc tỷ giá
        assertThat(a.headerGroupsSql(Direction.BAN_RA)).contains("ABS(h.TGTTHUE - h.TGTCTHUE * 0.10)");
        assertThat(adapter(true, PeriodBasis.NGAY_LAP, false, true).headerGroupsSql(Direction.BAN_RA))
                .doesNotContain("TGIA").contains("SUM(h.TGTCTHUE * 1)");
    }

    @Test
    void adjustmentWithoutRates_contributesZero_butStaysCounted() {
        // SI-61 (v3.8 D3): HĐ điều chỉnh thiếu THTTLTSUAT không lấy dòng hàng/header
        assertThat(a.lineRatesSql(Direction.BAN_RA)).contains("AND (h.TCHAT IS NULL OR h.TCHAT <> 3)");
        assertThat(a.headerGroupsSql(Direction.BAN_RA)).contains("WHEN h.TCHAT = 3 THEN 2");
        DorisSummaryAdapter off = adapter(true, PeriodBasis.NGAY_LAP, true, false);
        assertThat(off.lineRatesSql(Direction.BAN_RA)).doesNotContain("h.TCHAT <> 3");
        assertThat(off.headerGroupsSql(Direction.BAN_RA)).doesNotContain("h.TCHAT = 3");
    }

    @Test
    void inferredRate_matchesHeaderRatioWithinTolerance_neverInfers0Percent() {
        String e = DorisSummaryAdapter.inferredRateExpr(Jdk8Compat.listOf("10%", "8", "5.0%"), BigDecimal.ONE, new BigDecimal("0.0001"));
        assertThat(e).startsWith("CASE WHEN h.TGTCTHUE IS NULL OR h.TGTCTHUE = 0 OR h.TGTTHUE IS NULL OR h.TGTTHUE = 0 THEN NULL")
                .contains("WHEN ABS(h.TGTTHUE - h.TGTCTHUE * 0.10) <= GREATEST(1, ABS(h.TGTCTHUE) * 0.0001) THEN '10%'")
                .contains("* 0.08) <= GREATEST(1, ABS(h.TGTCTHUE) * 0.0001) THEN '8%'").contains("THEN '5%'")
                .endsWith("ELSE NULL END");
        assertThat(DorisSummaryAdapter.inferredRateExpr(Jdk8Compat.listOf(), BigDecimal.ONE, BigDecimal.ZERO))
                .isEqualTo("CAST(NULL AS STRING)");
        assertThatThrownBy(() -> DorisSummaryAdapter.inferredRateExpr(Jdk8Compat.listOf("0%"), BigDecimal.ONE, BigDecimal.ZERO))
                .hasMessageContaining("0%");
        assertThatThrownBy(() -> DorisSummaryAdapter.inferredRateExpr(Jdk8Compat.listOf("kct"), BigDecimal.ONE, BigDecimal.ZERO))
                .hasMessageContaining("dạng số");
        assertThat(a.headerGroupsSql(Direction.BAN_RA)).contains("THEN '10%'");
        assertThat(a.lineRatesSql(Direction.BAN_RA)).contains("MAX(CASE WHEN h.TGTCTHUE IS NULL");
    }
}
