package vn.teca.hddt.dataapi.infrastructure.doris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** DB-P1-03 (TD-API-34): HD8 / TMS tra theo lô — đường JOIN HOADON_HDR cũ và đường chỉ đọc HD_MST_LIST v5. */
class LookupSqlTest {
    private final DorisTables t = new DorisTables("datahub_hddt");

    private static int placeholders(String sql) {
        return sql.length() - sql.replace("?", "").length();
    }

    /** Tên cột kết quả (sau AS) của danh sách SELECT. */
    private static List<String> outputNames(String select) {
        List<String> out = new ArrayList<>();
        for (String part : select.split(",")) {
            String p = part.trim();
            int as = p.lastIndexOf(" AS ");
            out.add(as >= 0 ? p.substring(as + 4).trim() : p.substring(p.indexOf('.') + 1));
        }
        return out;
    }

    @Test
    void listSelect_hasSameOutputColumnsAsHeaderSelect_exceptHdrOnlyColumns() {
        // SI-65: HD8 v3 cần THTTLTSUAT/GCHDGOC — chỉ có ở HOADON_HDR (LIST v5 chưa có, KI-API-32)
        List<String> hdr = outputNames(InvoiceRecordColumns.SELECT);
        assertThat(hdr.subList(hdr.size() - 2, hdr.size())).containsExactly("THTTLTSUAT_JSON", "GCHDGOC");
        assertThat(outputNames(InvoiceRecordColumns.LIST_SELECT)).isEqualTo(hdr.subList(0, hdr.size() - 2));
        assertThat(InvoiceRecordColumns.LIST_SELECT).contains("l.TEN_DOI_TAC AS NBTEN").doesNotContain("h.");
        assertThat(InvoiceRecordColumns.SELECT).contains("CAST(h.THTTLTSUAT AS STRING) AS THTTLTSUAT_JSON");
        // Vì LIST thiếu hai cột trên, HD8 phải đọc HOADON_HDR kể cả khi bật list-serving
        assertThat(InvoiceRecordColumns.LIST_COVERS_RECORD).isFalse();
        assertThat(InvoiceRecordColumns.json("null")).isNull();
        assertThat(InvoiceRecordColumns.json("[]")).isEqualTo("[]");
    }

    @Test
    void hd8_purchases_listServingDropsHeaderJoinAndItsTdlapArgs() {
        String old = DorisInvoiceLookupAdapter.purchasesSql(t, false, 2, 3);
        String list = DorisInvoiceLookupAdapter.purchasesSql(t, true, 2, 3);
        assertThat(old).contains("JOIN `datahub_hddt`.`HOADON_HDR` h").contains("h.TDLAP >= ? AND h.TDLAP <= ?");
        assertThat(placeholders(old)).isEqualTo(3 + 2 + 3 + 2);
        assertThat(list).doesNotContain("HOADON_HDR").doesNotContain("h.")
                .contains("FROM `datahub_hddt`.`HD_MST_LIST` l WHERE l.MST = ? AND l.CHIEU = 'MUA_VAO' AND l.IS_ELIGIBLE = 1");
        assertThat(placeholders(list)).isEqualTo(3 + 2 + 3);
    }

    @Test
    void tms_refundTargets_listServingReadsKeyColumnsFromList() {
        String old = DorisInvoiceLookupAdapter.refundTargetsSql(t, false, 2, 3, 4);
        String list = DorisInvoiceLookupAdapter.refundTargetsSql(t, true, 2, 3, 4);
        assertThat(old).startsWith("SELECT h.NBMST, h.KHMSHDON").contains("h.TDLAP IN (?, ?, ?, ?)");
        assertThat(placeholders(old)).isEqualTo(1 + 2 + 3 + 4 + 4);
        assertThat(list).startsWith("SELECT l.NBMST, l.KHMSHDON, l.KHHDON, l.SHDON, l.TDLAP, l.LOAI_HD, l.CQT, l.ID, l.NMMST FROM")
                .doesNotContain("HOADON_HDR").doesNotContain("h.");
        assertThat(placeholders(list)).isEqualTo(1 + 2 + 3 + 4);
    }

    // ---- DB-P1-02 (TD-API-35): HD8 một truy vấn ứng viên/job + tra theo khóa literal ----

    private static vn.teca.hddt.dataapi.application.port.PurchaseCandidates.Ref ref(
            vn.teca.hddt.dataapi.application.port.PurchaseCandidates idx, int day, String loai, String id) {
        long fp = id.hashCode();
        idx.add(fp, java.time.LocalDate.of(2026, 9, day), "0101", loai, id, java.time.LocalDate.of(2026, 10, 1));
        return idx.lookup(fp).get(0);
    }

    @Test
    void hd8_candidatesSql_listOnly_noKyNoHeader_buyerPeriodFilters() {
        String sql = DorisInvoiceLookupAdapter.candidatesSql(t);
        assertThat(sql).startsWith("SELECT l.NBMST, l.KHHDON, l.SHDON, l.TDLAP, l.CQT, l.LOAI_HD, l.ID, l.KY FROM `datahub_hddt`.`HD_MST_LIST` l")
                .contains("l.MST = ? AND l.CHIEU = 'MUA_VAO' AND l.IS_ELIGIBLE = 1 AND l.TDLAP >= ? AND l.TDLAP <= ?")
                .doesNotContain("HOADON_HDR").doesNotContain("l.KY =").doesNotContain(" IN (");
        assertThat(placeholders(sql)).isEqualTo(3);
        assertThat(DorisInvoiceLookupAdapter.countCandidatesSql(t)).isEqualTo("SELECT COUNT(*) FROM `datahub_hddt`.`HD_MST_LIST` l"
                + sql.substring(sql.indexOf(" WHERE ")));
    }

    @Test
    void hd8_fetchStatements_groupByPhysicalPartition_max100IdsPerBranch_max50BranchesPerStatement() {
        vn.teca.hddt.dataapi.application.port.PurchaseCandidates idx = new vn.teca.hddt.dataapi.application.port.PurchaseCandidates();
        List<vn.teca.hddt.dataapi.application.port.PurchaseCandidates.Ref> refs = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            refs.add(ref(idx, 15, "INV", "a-" + i));       // 1 nhóm, 250 ID ⇒ 3 nhánh (100, 100, 50)
        }
        refs.add(ref(idx, 15, "MTT", "m-1"));              // khác LOAI_HD ⇒ nhánh riêng
        for (int d = 1; d <= 60; d++) {
            refs.add(ref(idx, d % 28 + 1, "INV", "d-" + d)); // 28 ngày khác (một ngày trùng nhóm a-*)
        }
        List<ExportSqlBuilder.BuiltSql> hdr = DorisInvoiceLookupAdapter.fetchStatements(t, false, "0100000002", refs);
        int branches = 0;
        int ids = 0;
        for (ExportSqlBuilder.BuiltSql q : hdr) {
            String[] parts = q.sql().split(" UNION ALL ");
            assertThat(parts.length).isLessThanOrEqualTo(50);
            for (String b : parts) {
                assertThat(b).contains("FROM `datahub_hddt`.`HOADON_HDR` h WHERE h.TDLAP = ? AND h.CQT = ? AND h.LOAI_HD = ? AND h.ID IN (")
                        .doesNotContain("HD_MST_LIST");
                int n = placeholders(b) - 3;
                assertThat(n).isBetween(1, 100);
                ids += n;
                branches++;
            }
            assertThat(placeholders(q.sql())).isEqualTo(q.args().size());
        }
        assertThat(ids).isEqualTo(refs.size());
        assertThat(branches).isGreaterThan(30);
        assertThat(hdr).hasSizeGreaterThan(0);
        assertThat(hdr.get(0).args().subList(0, 3)).containsExactly(java.time.LocalDate.of(2026, 9, 1), "0101", "INV");

        List<ExportSqlBuilder.BuiltSql> list = DorisInvoiceLookupAdapter.fetchStatements(t, true, "0100000002",
                refs.subList(0, 3));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).sql()).doesNotContain("HOADON_HDR").contains("l.TEN_DOI_TAC AS NBTEN")
                .contains("WHERE l.KY = ? AND l.MST = ? AND l.CHIEU = 'MUA_VAO' AND l.IS_ELIGIBLE = 1 AND l.TDLAP = ? AND l.LOAI_HD = ? AND l.CQT = ? AND l.ID IN (?, ?, ?)");
        assertThat(list.get(0).args()).containsExactly(java.time.LocalDate.of(2026, 10, 1), "0100000002",
                java.time.LocalDate.of(2026, 9, 15), "INV", "0101", "a-0", "a-1", "a-2");
    }
}
