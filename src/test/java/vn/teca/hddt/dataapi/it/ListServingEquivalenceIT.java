package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.zaxxer.hikari.HikariDataSource;

import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.RefundTarget;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisInvoiceLookupAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisTables;
import vn.teca.hddt.dataapi.infrastructure.doris.ExportSqlBuilder;
import vn.teca.hddt.dataapi.support.TestProps;

/**
 * DB-P1-03 (TD-API-34) trên Doris thật: database DDL v5 CÓ DỮ LIỆU do pipeline toolkit ghi (IT_LIST_DB — vd. DB tạm bf_it_*
 * mà {@code DorisApplyIT -Dbackfill.it.ddl=v5 -Dbackfill.it.keep=true} để lại). So TỪNG DÒNG kết quả của đường cũ (LIST ⨝
 * HOADON_HDR) với đường {@code hddt.doris.list-serving} (chỉ HD_MST_LIST): CSV HD1/HD5 với mọi tổ hợp bộ lọc, HD8, TMS.
 * IT_LIST_DB_DROP=true ⇒ xóa database khi xong (chỉ nhận tên bf_it_&lt;số&gt;). Không in dữ liệu hóa đơn ra log.
 */
class ListServingEquivalenceIT {
    /** Trạng thái người mua thử 0100000002 = 05 (thuộc danh sách loại) để bộ lọc lbttmsthue có tác dụng. */
    static final String STATUS_SQL = "SELECT '0100000002' AS MST, NULL AS MST_CU, '05' AS TRANG_THAI";
    static HikariDataSource ds;
    static JdbcTemplate jdbc;
    static String db;

    @BeforeAll
    static void connect() {
        db = ItInfra.env("IT_LIST_DB");
        Assumptions.assumeTrue(db != null, "Không có IT_LIST_DB ⇒ bỏ qua");
        ds = ItInfra.doris();
        jdbc = new JdbcTemplate(ds);
        jdbc.setQueryTimeout(120);
    }

    @AfterAll
    static void close() {
        if (jdbc != null && "true".equals(ItInfra.env("IT_LIST_DB_DROP")) && db.matches("bf_it_\\d+")) {
            jdbc.execute("DROP DATABASE IF EXISTS `" + db + "` FORCE");
        }
        if (ds != null) {
            ds.close();
        }
    }

    static HddtProperties props(boolean listServing) {
        HddtProperties p = TestProps.defaults();
        return new HddtProperties(p.api(), new HddtProperties.Doris(null, null, null, db, 4, 120, 7200, 600, listServing),
                p.dse(), p.s3(), new HddtProperties.MstStatus(STATUS_SQL, Jdk8Compat.listOf("03", "05", "06")), p.hd1(), p.hd5(),
                p.hd7(), p.hd8(), p.tms(), p.export(), p.worker(), p.security(), Jdk8Compat.mapOf());
    }

    /** Như DorisExportAdapter.bounds: khoảng TDLAP thực tế của HĐ mua vào (đường cũ). */
    static ExportSqlBuilder.TdlapBounds bounds(ExportSqlBuilder b, ExportSpec spec) {
        if (spec.direction() != Direction.MUA_VAO || !b.needsPurchaseBounds()) {
            return null;
        }
        ExportSqlBuilder.BuiltSql q = b.purchaseTdlapBounds(spec);
        return jdbc.query(q.sql(), (ResultSetExtractor<ExportSqlBuilder.TdlapBounds>) rs -> {
            LocalDate min = rs.next() ? rs.getObject(1, LocalDate.class) : null;
            return min == null ? new ExportSqlBuilder.TdlapBounds(spec.range().firstDay(), spec.range().firstDay())
                    : new ExportSqlBuilder.TdlapBounds(min, rs.getObject(2, LocalDate.class));
        }, q.args().toArray());
    }

    static List<String> lines(ExportSqlBuilder b, ExportSpec spec) {
        ExportSqlBuilder.BuiltSql q = b.select(spec, 120, false, bounds(b, spec));
        List<String> out = new ArrayList<>(jdbc.queryForList(q.sql(), String.class, q.args().toArray()));
        Collections.sort(out);
        return out;
    }

    @Test
    void hd1Hd5_csvLines_identical_forEveryFilterCombination() {
        DorisTables t = new DorisTables(db);
        HddtProperties p = props(false);
        List<Map<String, Object>> groups = jdbc.queryForList("SELECT DISTINCT MST, CHIEU, KY FROM `" + db + "`.`HD_MST_LIST`");
        Assumptions.assumeFalse(groups.isEmpty(), "IT_LIST_DB chưa có dữ liệu HD_MST_LIST");
        int compared = 0;
        int nonEmpty = 0;
        for (Map<String, Object> g : groups) {
            LocalDate ky = ((java.sql.Date) g.get("KY")).toLocalDate();
            String month = String.format("%02d/%d", ky.getMonthValue(), ky.getYear());
            Direction d = Direction.valueOf((String) g.get("CHIEU"));
            for (int mask = 0; mask < 8; mask++) {
                boolean status = (mask & 1) != 0;
                boolean illegal = (mask & 2) != 0;
                boolean form = (mask & 4) != 0;
                ExportSpec spec = new ExportSpec(UUID.randomUUID(), 1, d, (String) g.get("MST"), MonthRange.parse(month, month, 12),
                        status, Jdk8Compat.listOf("03", "05", "06"), illegal, "hddt", "x/");
                List<String> old = lines(new ExportSqlBuilder(t, p.mstStatus(), form, form, false), spec);
                List<String> list = lines(new ExportSqlBuilder(t, p.mstStatus(), form, form, true), spec);
                assertThat(list).as("%s %s mask=%d", d, month, mask).isEqualTo(old);
                compared++;
                nonEmpty += old.isEmpty() ? 0 : 1;
            }
        }
        System.out.println("LIST-SERVING CSV: " + compared + " so sánh, " + nonEmpty + " có dòng — khớp từng byte");
        assertThat(nonEmpty).isPositive();
    }

    @Test
    void hd8_purchases_and_tms_refundTargets_identical() {
        DorisInvoiceLookupAdapter old = new DorisInvoiceLookupAdapter(jdbc, props(false));
        DorisInvoiceLookupAdapter list = new DorisInvoiceLookupAdapter(jdbc, props(true));
        int purchases = 0;
        for (Map<String, Object> b : jdbc.queryForList("SELECT DISTINCT MST FROM `" + db + "`.`HD_MST_LIST` WHERE CHIEU = 'MUA_VAO'")) {
            String buyer = (String) b.get("MST");
            List<InvoiceKey> keys = new ArrayList<>();
            LocalDate from = null;
            LocalDate to = null;
            for (Map<String, Object> r : jdbc.queryForList("SELECT NBMST, KHMSHDON, KHHDON, SHDON, TDLAP FROM `" + db
                    + "`.`HD_MST_LIST` WHERE MST = ? AND CHIEU = 'MUA_VAO'", buyer)) {
                keys.add(new InvoiceKey((String) r.get("NBMST"), ((Number) r.get("KHMSHDON")).intValue(), (String) r.get("KHHDON"),
                        ((Number) r.get("SHDON")).longValue()));
                LocalDate td = ((java.sql.Date) r.get("TDLAP")).toLocalDate();
                from = from == null || td.isBefore(from) ? td : from;
                to = to == null || td.isAfter(to) ? td : to;
            }
            keys.add(new InvoiceKey("0100000099", 1, "C26TAA", 999_999));
            List<InvoiceRecord> a = old.findPurchases(buyer, from, to, keys);
            List<InvoiceRecord> c = list.findPurchases(buyer, from, to, keys);
            assertThat(new HashSet<>(c)).isEqualTo(new HashSet<>(a));
            assertThat(c).hasSameSizeAs(a);
            // DB-P1-02: một truy vấn ứng viên/job + tra theo khóa literal — HDR (cũ) và LIST v5 (list-serving)
            java.util.Set<Long> fps = new HashSet<>();
            keys.forEach(k -> fps.add(k.lookupFingerprint()));
            for (DorisInvoiceLookupAdapter ad : java.util.Arrays.asList(old, list)) {
                List<InvoiceRecord> e = ad.fetchPurchases(buyer, ad.indexPurchases(buyer, from, to, fps::contains), keys);
                assertThat(new HashSet<>(e)).isEqualTo(new HashSet<>(a));
                assertThat(e).hasSameSizeAs(a);
            }
            purchases += a.size();
        }
        List<DatedKey> sales = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT NBMST, KHMSHDON, KHHDON, SHDON, TDLAP FROM `" + db
                + "`.`HD_MST_LIST` WHERE CHIEU = 'BAN_RA'")) {
            sales.add(new DatedKey(new InvoiceKey((String) r.get("NBMST"), ((Number) r.get("KHMSHDON")).intValue(),
                    (String) r.get("KHHDON"), ((Number) r.get("SHDON")).longValue()), ((java.sql.Date) r.get("TDLAP")).toLocalDate()));
        }
        List<RefundTarget> ta = old.findRefundTargets(sales);
        List<RefundTarget> tc = list.findRefundTargets(sales);
        assertThat(new HashSet<>(tc)).isEqualTo(new HashSet<>(ta));
        System.out.println("LIST-SERVING HD8: " + purchases + " HĐ khớp; TMS: " + ta.size() + " HĐ đích khớp");
        assertThat(purchases).isPositive();
        assertThat(ta).isNotEmpty();
    }
}
