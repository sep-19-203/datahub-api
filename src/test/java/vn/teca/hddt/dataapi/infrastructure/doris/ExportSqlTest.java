package vn.teca.hddt.dataapi.infrastructure.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.port.ExportPort.ExportReport;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.infrastructure.doris.CsvLineSql;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisExportAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisTables;
import vn.teca.hddt.dataapi.infrastructure.doris.ExportSqlBuilder;
import vn.teca.hddt.dataapi.support.TestProps;

class ExportSqlTest {
    final HddtProperties props = TestProps.defaults();
    final ExportSqlBuilder builder = new ExportSqlBuilder(new DorisTables("datahub_hddt"), props.mstStatus());

    ExportSpec spec(Direction d, boolean status, boolean illegal, String mst) {
        return new ExportSpec(UUID.randomUUID(), 1, d, mst, MonthRange.parse("01/2026", "03/2026", 12), status,
                Jdk8Compat.listOf("03", "05", "06"), illegal, "hddt", "export/query-result/x/");
    }

    /** Đếm phần tử mức đỉnh của CONCAT_WS(',', e1, e2, ...). */
    static int countTopLevelArgs(String concatWs) {
        String inner = concatWs.substring("CONCAT_WS(',',".length(), concatWs.length() - 1);
        int depth = 0;
        int count = 1;
        boolean inStr = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\'') {
                inStr = !inStr;
            } else if (!inStr && c == '(') {
                depth++;
            } else if (!inStr && c == ')') {
                depth--;
            } else if (!inStr && depth == 0 && c == ',') {
                count++;
            }
        }
        return count;
    }

    // ---- DB-P1-03 (TD-API-34): hddt.doris.list-serving — chỉ đọc HD_MST_LIST DDL v5 ----

    final ExportSqlBuilder listOnly = new ExportSqlBuilder(new DorisTables("datahub_hddt"), props.mstStatus(), true, true, true);

    @Test
    void listServing_csvExpression_isOldExpressionOnListColumns_whenLayoutCovered() {
        // HD1 v3 và HD5 từ 08/11 (SI-63) cần THTTLTSUAT, GCHDGOC — chỉ ở HDR ⇒ không có biểu thức LIST
        // (SI-52, KI-API-32).
        assertThat(CsvLineSql.listCovers(CsvLayouts.HD5_SALES)).isFalse();
        assertThat(CsvLineSql.listCovers(CsvLayouts.HD1_PURCHASE)).isFalse();
        assertThatThrownBy(() -> CsvLineSql.lineExpression(CsvLayouts.HD1_PURCHASE, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("thttltsuat");
        assertThatThrownBy(() -> CsvLineSql.lineExpression(CsvLayouts.HD5_SALES, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("thttltsuat");
    }

    @Test
    void listServing_hd1V3_fallsBackToHeaderJoin_untilListHasNewColumns() {
        // Bật list-serving vẫn ra đúng SQL HDR như khi tắt (kể cả truy vấn khoảng TDLAP)
        ExportSpec s = spec(Direction.MUA_VAO, true, true, "0100000001");
        ExportSqlBuilder.TdlapBounds b = new ExportSqlBuilder.TdlapBounds(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 3, 31));
        assertThat(listOnly.select(s, 60, false, b)).isEqualTo(builder.select(s, 60, false, b));
        assertThat(listOnly.countUpTo(s, 10, 30, b)).isEqualTo(builder.countUpTo(s, 10, 30, b));
        assertThat(listOnly.select(s, 60, false, b).sql()).contains("HOADON_HDR").contains("THTTLTSUAT").contains("h.GCHDGOC");
        assertThat(listOnly.needsPurchaseBounds()).isTrue();
    }

    @Test
    void listServing_hd5V0811_fallsBackToHeaderJoin_untilListHasNewColumns() {
        // 08/11 (SI-63): HD5 có thttltsuat, lddctthe (LIST v5 không có) ⇒ như HD1 v3: bật list-serving vẫn ra SQL HDR
        ExportSpec s = spec(Direction.BAN_RA, true, true, "0100000001");
        assertThat(listOnly.select(s, 60, false, null)).isEqualTo(builder.select(s, 60, false, null));
        assertThat(listOnly.countUpTo(s, 10, 30, null)).isEqualTo(builder.countUpTo(s, 10, 30, null));
        assertThat(listOnly.select(s, 60, false, null).sql()).contains("HOADON_HDR").contains("THTTLTSUAT").contains("h.GCHDGOC");
        // needsPurchaseBounds chỉ hỏi cho mua vào (HD1) — HD1 v3 luôn đọc HDR ⇒ true ở cả hai chế độ
        assertThat(builder.needsPurchaseBounds()).isTrue();
    }

    @Test
    void lineExpression_hasExactly15And18Elements_andNoNullableElement() {
        String hd1 = CsvLineSql.lineExpression(CsvLayouts.HD1_PURCHASE);
        String hd5 = CsvLineSql.lineExpression(CsvLayouts.HD5_SALES);
        assertThat(countTopLevelArgs(hd1)).isEqualTo(15);
        assertThat(countTopLevelArgs(hd5)).isEqualTo(18);
        assertThat(hd1).contains("IFNULL(CONCAT('\"', REPLACE(h.NBMST, '\"', '\"\"'), '\"'), '')");
        assertThat(hd1).contains("IFNULL(CAST(h.TGTCTHUE AS STRING), '')");
        assertThat(hd1).contains("IFNULL(DATE_FORMAT(h.BHPNGAY, '%Y-%m-%d %H:%i:%s'), '')");
        assertThat(hd5).contains("h.TGTTTBSO").contains("h.NMTEN").contains("h.NKY");
        assertThat(hd1).doesNotContain("h.TGTTTBSO");
        // HD1 v3 (SI-52): bỏ nky (gạch), thttltsuat JSON bọc nháy (JSON null ⇒ rỗng), lddctthe cuối dòng
        assertThat(hd1).doesNotContain("h.NKY").doesNotContain("@hdr:");
        assertThat(hd1).contains("IFNULL(CONCAT('\"', REPLACE(NULLIF(CAST(h.THTTLTSUAT AS STRING), 'null'), '\"', '\"\"'),"
                + " '\"'), '')");
        assertThat(hd1).endsWith("IFNULL(CONCAT('\"', REPLACE(h.GCHDGOC, '\"', '\"\"'), '\"'), ''))");
        // HD5 sheet 08/11 (SI-63): thttltsuat JSON như HD1 (sau tgtthue), lddctthe cuối dòng, cùng nguồn HD1/HD3 (SI-53)
        assertThat(hd5).doesNotContain("@hdr:").contains("IFNULL(CAST(h.TGTTHUE AS STRING), ''),IFNULL(CONCAT('\"', REPLACE(NULLIF(CAST(h.THTTLTSUAT AS STRING), 'null')");
        assertThat(hd5).endsWith("IFNULL(CONCAT('\"', REPLACE(h.GCHDGOC, '\"', '\"\"'), '\"'), ''))");
    }

    @Test
    void noTaxRateAmountColumns_afterSpecRemoval() {
        // 06/11: 13 trường pctkct_sotien … pctkhac_sothue bị gạch khỏi đặc tả ⇒ không xuất, không đọc cột PCT* của Doris
        for (vn.teca.hddt.dataapi.domain.csv.CsvLayout layout : Jdk8Compat.listOf(CsvLayouts.HD1_PURCHASE, CsvLayouts.HD5_SALES)) {
            String line = CsvLineSql.lineExpression(layout);
            assertThat(line).doesNotContain("PCT").contains("h.BHPHAP");
            assertThat(layout.names()).noneMatch(n -> n.startsWith("pct"));
        }
    }

    @Test
    void hd1_filtersBuyerPeriodFromList_andSellerStatus() {
        ExportSqlBuilder.BuiltSql q = builder.select(spec(Direction.MUA_VAO, true, true, "0100000001"), 7200, false);
        assertThat(q.sql()).contains("l.CHIEU = 'MUA_VAO'").contains("s ON s.MST = h.NBMST")
                .contains("s.TRANG_THAI NOT IN (?, ?, ?)").contains("(h.BHPHAP IS NULL OR h.BHPHAP <> 1)")
                .contains("l.IS_ELIGIBLE = 1").contains("SET_VAR(query_timeout = 7200, enable_parallel_outfile = false)")
                .doesNotContain("h.TDLAP >= ?"); // kỳ mua vào theo NKY→NCMA→TDLAP: không cắt theo TDLAP
        assertThat(q.args()).containsExactlyElementsOf(withStatusKeys("0100000001", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1), "0100000001", "03", "05", "06"));
    }

    @Test
    void hd1_withTdlapBounds_prunesHeaderPartitions_withoutChangingPeriodRule() {
        ExportSqlBuilder.BuiltSql q = builder.select(spec(Direction.MUA_VAO, false, false, "0100000001"), 60, false,
                new ExportSqlBuilder.TdlapBounds(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 3, 31)));
        assertThat(q.sql()).contains("l.KY >= ? AND l.KY <= ?").contains("h.TDLAP >= ? AND h.TDLAP <= ?");
        assertThat(q.args()).containsExactlyElementsOf(withStatusKeys("0100000001", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1), "0100000001", LocalDate.of(2025, 12, 20), LocalDate.of(2026, 3, 31)));
        ExportSqlBuilder.BuiltSql b = builder.purchaseTdlapBounds(spec(Direction.MUA_VAO, false, false, "0100000001"));
        assertThat(b.sql()).contains("MIN(l.TDLAP)").contains("l.CHIEU = 'MUA_VAO' AND l.IS_ELIGIBLE = 1");
    }

    @Test
    void formRuleR1_appliesToHd1AndHd5_andEachCanBeDisabled() {
        String rule = "(h.KHMSHDON = 1 OR (h.KHMSHDON = 5 AND IFNULL(h.TGTTHUE, 0) <> 0))";
        assertThat(builder.select(spec(Direction.MUA_VAO, false, false, "0100000001"), 60, false).sql()).endsWith(" AND " + rule);
        assertThat(builder.select(spec(Direction.BAN_RA, false, false, "0100000002"), 60, false).sql()).endsWith(" AND " + rule);
        ExportSqlBuilder off = new ExportSqlBuilder(new DorisTables("datahub_hddt"), props.mstStatus(), false);
        assertThat(off.select(spec(Direction.MUA_VAO, false, false, "0100000001"), 60, false).sql()).doesNotContain(rule);
        assertThat(off.select(spec(Direction.BAN_RA, false, false, "0100000002"), 60, false).sql()).doesNotContain(rule);
        ExportSqlBuilder salesOff = new ExportSqlBuilder(new DorisTables("datahub_hddt"), props.mstStatus(), true, false);
        assertThat(salesOff.select(spec(Direction.MUA_VAO, false, false, "0100000001"), 60, false).sql()).contains(rule);
        assertThat(salesOff.select(spec(Direction.BAN_RA, false, false, "0100000002"), 60, false).sql()).doesNotContain(rule);
    }

    @Test
    void hd5_countUpTo_usesSalesFiltersAndPartitionPruning_withoutBoundsQuery() {
        ExportSpec s = spec(Direction.BAN_RA, true, true, "0100000002");
        ExportSqlBuilder.BuiltSql count = builder.countUpTo(s, 10_000, 30, null);
        assertThat(count.sql()).contains("l.CHIEU = 'BAN_RA'").contains("h.TDLAP >= ? AND h.TDLAP < ?")
                .contains("s ON s.MST = h.NMMST").contains("(h.BHPHAP IS NULL OR h.BHPHAP <> 1)")
                .contains("h.KHMSHDON = 1").endsWith(" LIMIT ?) c");
        assertThat(count.args()).containsExactlyElementsOf(withStatusKeys("0100000002", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1), "0100000002", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), "03", "05", "06",
                10_000L));
    }

    @Test
    void countUpTo_reusesExportFilters_andStopsAtLimit() {
        ExportSpec s = spec(Direction.MUA_VAO, true, true, "0100000001");
        ExportSqlBuilder.TdlapBounds b = new ExportSqlBuilder.TdlapBounds(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 3, 31));
        ExportSqlBuilder.BuiltSql count = builder.countUpTo(s, 10_000, 30, b);
        String exportSql = builder.select(s, 7200, false, b).sql();
        String exportFromWhere = exportSql.substring(exportSql.indexOf(" FROM `datahub_hddt`.`HD_MST_LIST`"));
        assertThat(count.sql()).startsWith("SELECT /*+ SET_VAR(query_timeout = 30) */ COUNT(*) AS N FROM (SELECT 1 AS x")
                .contains(exportFromWhere + " LIMIT ?) c").doesNotContain("csv_line");
        List<Object> expected = new java.util.ArrayList<>(builder.select(s, 7200, false, b).args());
        expected.add(10_000L);
        assertThat(count.args()).containsExactlyElementsOf(expected);
    }

    @Test
    void hd5_filtersSellerPeriodByTdlap_andBuyerStatus() {
        ExportSqlBuilder.BuiltSql q = builder.select(spec(Direction.BAN_RA, true, false, "0100000002"), 7200, false);
        assertThat(q.sql()).contains("l.CHIEU = 'BAN_RA'").contains("s ON s.MST = h.NMMST")
                .contains("h.TDLAP >= ? AND h.TDLAP < ?").doesNotContain("BHPHAP <> 1");
        assertThat(q.args()).containsExactlyElementsOf(withStatusKeys("0100000002", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1), "0100000002", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), "03", "05", "06"));
    }

    /** Tham số truy vấn con giới hạn MST đối tác (TD-API-39): (kỳ đầu, kỳ cuối, MST) × 2 nhánh, đứng trước tham số WHERE. */
    private static List<Object> withStatusKeys(String mst, Object... whereArgs) {
        List<Object> out = new java.util.ArrayList<>();
        for (int branch = 0; branch < 2; branch++) {
            out.add(LocalDate.of(2026, 1, 1));
            out.add(LocalDate.of(2026, 3, 1));
            out.add(mst);
        }
        out.addAll(java.util.Arrays.asList(whereArgs));
        return out;
    }

    @Test
    void partnerStatus_isLookedUpOnlyForThisExportsPartners_byMstThenOldMst() {
        String hd1 = builder.select(spec(Direction.MUA_VAO, true, true, "0100000001"), 60, false).sql();
        String keys1 = "(SELECT lk.NBMST FROM `datahub_hddt`.`HD_MST_LIST` lk WHERE lk.KY >= ? AND lk.KY <= ? AND lk.MST = ?"
                + " AND lk.CHIEU = 'MUA_VAO' AND lk.IS_ELIGIBLE = 1)";
        assertThat(hd1).contains("WHERE st0.MST IN " + keys1).contains("WHERE st0.MST_CU IN " + keys1 + " AND st0.MST_CU <> ''")
                .contains("s ON s.MST = h.NBMST");
        String hd5 = builder.select(spec(Direction.BAN_RA, true, true, "0100000002"), 60, false).sql();
        assertThat(hd5).contains("WHERE st0.MST IN (SELECT lk.NMMST FROM").contains("lk.CHIEU = 'BAN_RA'")
                .contains("s ON s.MST = h.NMMST");
        // list-serving: HD1 v3 (15 cột) và HD5 (18 cột, 08/11) đều có cột chỉ ở HOADON_HDR ⇒ vẫn đi đường HDR, cùng cách tra
        String viaListFlag = listOnly.select(spec(Direction.BAN_RA, true, true, "0100000002"), 60, false).sql();
        assertThat(viaListFlag).contains("WHERE st0.MST_CU IN (SELECT lk.NMMST FROM").contains("s ON s.MST = h.NMMST");
    }

    @Test
    void everyPlaceholderHasAnArgument_inAllExportVariants() {
        ExportSqlBuilder.TdlapBounds b = new ExportSqlBuilder.TdlapBounds(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 3, 31));
        for (ExportSqlBuilder x : Jdk8Compat.listOf(builder, listOnly)) {
            for (Direction d : Direction.values()) {
                for (boolean excludeStatus : new boolean[]{true, false}) {
                    for (boolean excludeIllegal : new boolean[]{true, false}) {
                        ExportSpec s = spec(d, excludeStatus, excludeIllegal, "0100000003");
                        for (ExportSqlBuilder.BuiltSql q : Jdk8Compat.listOf(x.select(s, 60, false, b), x.countUpTo(s, 5, 30, b))) {
                            long marks = q.sql().chars().filter(c -> c == '?').count();
                            assertThat(marks).as(d + " " + excludeStatus + " " + excludeIllegal + ": " + q.sql())
                                    .isEqualTo(q.args().size());
                        }
                    }
                }
            }
        }
    }

    @Test
    void userInputNeverConcatenatedIntoSql() {
        String evil = "0100000001') OR 1=1 --";
        ExportSqlBuilder.BuiltSql q = builder.select(spec(Direction.MUA_VAO, false, false, evil), 60, false);
        assertThat(q.sql()).doesNotContain(evil).doesNotContain("OR 1=1");
        assertThat(q.args()).contains(evil);
    }

    @Test
    void outfileClause_hasS3Props_andRejectsInjectableConfig() {
        String c = ExportSqlBuilder.outfileClause("hddt", "export/query-result/j/", props.s3(), props.export());
        assertThat(c).startsWith(" INTO OUTFILE \"s3://hddt/export/query-result/j/\" FORMAT AS CSV PROPERTIES (")
                .contains("\"s3.endpoint\" = \"http://minio.test:9000\"").contains("\"use_path_style\" = \"true\"")
                .contains("\"success_file_name\" = \"_SUCCESS\"").contains("\"max_file_size\" = \"5MB\"");
        HddtProperties.S3 bad = new HddtProperties.S3("http://x\" , \"evil\" = \"1", "r", "a", "s", true, "hddt", "p", "v",
                "i", Jdk8Compat.listOf("hddt"), Jdk8Compat.listOf(), Jdk8Compat.listOf(), null, null, null);
        assertThatThrownBy(() -> ExportSqlBuilder.outfileClause("hddt", "p/", bad, props.export()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidDatabaseNameRejected() {
        assertThatThrownBy(() -> new DorisTables("datahub_hddt`; SELECT 1; --")).isInstanceOf(IllegalArgumentException.class);
    }

    static ResultSet outfileResult(String files, String rows, String size, String url) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        String[] labels = {"FileNumber", "TotalRows", "FileSize", "URL"};
        when(md.getColumnCount()).thenReturn(4);
        for (int i = 0; i < 4; i++) {
            when(md.getColumnLabel(i + 1)).thenReturn(labels[i]);
        }
        when(rs.getMetaData()).thenReturn(md);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong(1)).thenReturn(Long.parseLong(files));
        when(rs.getLong(2)).thenReturn(Long.parseLong(rows));
        when(rs.getLong(3)).thenReturn(Long.parseLong(size));
        when(rs.getString(4)).thenReturn(url);
        return rs;
    }

    @Test
    void outfileReport_singleWriter_keepsDorisUrlPattern() throws Exception {
        ResultSet rs = outfileResult("2", "100000", "4567",
                "s3://hddt/export/query-result/j/c852ec478bc8afffb49e7-a82661eec9d995ad_");
        ExportReport r = DorisExportAdapter.parseOutfileResult(rs, "hddt", "export/query-result/j/", false);
        assertThat(r.url()).isEqualTo("s3://hddt/export/query-result/j/c852ec478bc8afffb49e7-a82661eec9d995ad_*");
        assertThat(r.rowCount()).isEqualTo(100000);
        assertThat(r.fileCount()).isEqualTo(2);
        assertThat(r.dataKeyPrefix()).isEqualTo("export/query-result/j/c852ec478bc8afffb49e7-a82661eec9d995ad_");
    }

    @Test
    void outfileReport_outsideJobPrefixOrBucket_isRejected() throws Exception {
        assertThatThrownBy(() -> DorisExportAdapter.parseOutfileResult(outfileResult("1", "1", "1",
                "s3://other/export/query-result/j/x_"), "hddt", "export/query-result/j/", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DorisExportAdapter.parseOutfileResult(outfileResult("1", "1", "1",
                "s3://hddt/export/query-result/other/x_"), "hddt", "export/query-result/j/", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeholderHelperNeverAcceptsEmptyInList() {
        assertThatThrownBy(() -> DorisTables.placeholders(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DorisTables.placeholders(3)).isEqualTo("?, ?, ?");
    }
}
