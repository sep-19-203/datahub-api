package vn.teca.hddt.dataapi.infrastructure.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** HD3 (TD-API-36): khóa vật lý từ HD_MST_LIST rồi đọc HOADON_HDR theo giá trị khóa literal (như HD8 TD-API-35). */
class Hd3QuerySqlTest {
    final DorisTables t = new DorisTables("datahub_hddt");
    final Set<String> variants = new LinkedHashSet<>(Jdk8Compat.listOf("C26TAA", "C26TĐA"));
    final LocalDate tdlap = LocalDate.of(2026, 10, 28);

    @Test
    void stepA_readsOnlyListPartitionAndBucket_returnsPhysicalKeys() {
        ExportSqlBuilder.BuiltSql a = DorisInvoiceQueryAdapter.headerKeysSql(t, "0100000002", variants, 99L, tdlap);
        assertThat(a.sql()).isEqualTo("SELECT l.CQT, l.LOAI_HD, l.ID FROM `datahub_hddt`.`HD_MST_LIST` l"
                + " WHERE l.KY = ? AND l.MST = ? AND l.CHIEU = 'BAN_RA' AND l.TDLAP = ? AND l.SHDON = ? AND l.KHHDON IN (?, ?)"
                + " ORDER BY l.LOAI_HD, l.ID LIMIT 100");
        assertThat(a.args()).containsExactly(LocalDate.of(2026, 10, 1), "0100000002", tdlap, 99L, "C26TAA", "C26TĐA");
        assertThat(a.sql()).doesNotContain("HOADON_HDR");
    }

    @Test
    void stepB_readsHeaderByLiteralKey_keepsExactLookupConditions_andSelectsLddctthe() {
        ExportSqlBuilder.BuiltSql b = DorisInvoiceQueryAdapter.headersByKeySql(t, tdlap, "0100", "INV",
                Jdk8Compat.listOf("id-1", "id-2"), "0100000002", 1, 99L, variants);
        assertThat(b.sql()).isEqualTo("SELECT " + DorisInvoiceQueryAdapter.HEADER_COLUMNS + " FROM `datahub_hddt`.`HOADON_HDR` h"
                + " WHERE h.TDLAP = ? AND h.CQT = ? AND h.LOAI_HD = ? AND h.ID IN (?, ?)"
                + " AND h.NBMST = ? AND h.KHMSHDON = ? AND h.SHDON = ? AND h.KHHDON IN (?, ?)");
        assertThat(b.args()).containsExactly(tdlap, "0100", "INV", "id-1", "id-2", "0100000002", 1, 99L, "C26TAA", "C26TĐA");
        // không JOIN, không truy vấn con: Doris cắt partition theo TDLAP và bucket theo ID (IN ≤ 100)
        assertThat(b.sql()).doesNotContain("JOIN").doesNotContain("HD_MST_LIST");
        assertThat(DorisInvoiceQueryAdapter.HEADER_COLUMNS).contains("h.GCHDGOC").contains("CAST(h.THTTLTSUAT AS STRING)");
    }

    @Test
    void stepB_rejectsMoreThan100IdsPerBranch_andUserInputStaysInArgs() {
        List<String> ids = new ArrayList<>(Collections.nCopies(DorisInvoiceQueryAdapter.MAX_KEYS + 1, "x"));
        assertThatThrownBy(() -> DorisInvoiceQueryAdapter.headersByKeySql(t, tdlap, "0100", "INV", ids, "0100000002", 1, 99L,
                variants)).isInstanceOf(IllegalArgumentException.class);
        String evil = "0100000002') OR 1=1 --";
        assertThat(DorisInvoiceQueryAdapter.headerKeysSql(t, evil, variants, 99L, tdlap).sql()).doesNotContain(evil);
    }
}
