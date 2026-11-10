package vn.teca.hddt.dataapi.infrastructure.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** SI-65: trạng thái MST từ NNT_ALL_STATUS, tra theo MST rồi tới MST cũ; TD-API-39: lọc nguồn trước khi gộp. */
class MstStatusSqlTest {
    static final String SRC = "SELECT MST, MST_CU, TRANG_THAI FROM doris.NNT_ALL_STATUS";
    final HddtProperties.MstStatus cfg = new HddtProperties.MstStatus(SRC, Jdk8Compat.listOf("03", "05", "06"));

    @Test
    void lookup_matchesMstFirst_thenOldMst_andKeepsOneRowPerMst() {
        String d = MstStatusSql.derivedTable(cfg, "(?, ?)");
        assertThat(d).isEqualTo("(SELECT st1.MST AS MST,"
                + " IF(MAX(st1.TRUC_TIEP) = 1, MAX(IF(st1.TRUC_TIEP = 1, st1.TRANG_THAI, NULL)), MAX(st1.TRANG_THAI)) AS TRANG_THAI"
                + " FROM (SELECT st0.MST AS MST, CAST(st0.TRANG_THAI AS STRING) AS TRANG_THAI, 1 AS TRUC_TIEP FROM (" + SRC
                + ") st0 WHERE st0.MST IN (?, ?)"
                + " UNION ALL SELECT st0.MST_CU AS MST, CAST(st0.TRANG_THAI AS STRING) AS TRANG_THAI, 0 AS TRUC_TIEP FROM (" + SRC
                + ") st0 WHERE st0.MST_CU IN (?, ?) AND st0.MST_CU <> '') st1 GROUP BY st1.MST)");
    }

    @Test
    void withoutKeyFilter_readsWholeSource_butSkipsBlankOldMst() {
        String d = MstStatusSql.derivedTable(cfg);
        assertThat(d).contains("FROM (" + SRC + ") st0 UNION ALL")
                .contains(") st0 WHERE st0.MST_CU IS NOT NULL AND st0.MST_CU <> '') st1 GROUP BY st1.MST)")
                .doesNotContain("?");
    }

    @Test
    void missingOrUnsafeSource_isRejected() {
        assertThatThrownBy(() -> MstStatusSql.derivedTable(new HddtProperties.MstStatus(" ", Jdk8Compat.listOf("03"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MstStatusSql.derivedTable(new HddtProperties.MstStatus(SRC + ";", Jdk8Compat.listOf("03"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void taxpayerStatusAdapter_passesMstListToBothBranchesAndOuterWhere() {
        String one = DorisTaxpayerStatusAdapter.statusSql(cfg, "s.TRANG_THAI", 1);
        assertThat(one).startsWith("SELECT s.TRANG_THAI FROM (SELECT st1.MST AS MST,").endsWith(" s WHERE s.MST IN (?)")
                .contains("WHERE st0.MST IN (?)").contains("WHERE st0.MST_CU IN (?) AND st0.MST_CU <> ''");
        assertThat(one.chars().filter(c -> c == '?').count()).isEqualTo(3);
        String three = DorisTaxpayerStatusAdapter.statusSql(cfg, "s.MST, s.TRANG_THAI", 3);
        assertThat(three.chars().filter(c -> c == '?').count()).isEqualTo(9);
    }
}
