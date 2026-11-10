package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import vn.teca.hddt.dataapi.application.port.PurchaseCandidates;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisInvoiceLookupAdapter;

/**
 * DB-P1-02 (TD-API-35) trên Doris DEV (CHỈ ĐỌC datahub_hddt): cùng tập khóa HD8 qua đường cũ (mỗi lô 500 một truy vấn LIST ⨝
 * HDR) và đường mới (một truy vấn ứng viên/job + tra theo khóa literal mỗi lô) phải trả CÙNG bản ghi. Người mua = MST có
 * nhiều HĐ mua vào nhất; ngày lập = IT_HD8_DAY (yyyy-MM-dd) hoặc ngày nhiều HĐ nhất của người mua đó; số khóa mẫu = IT_HD8_KEYS
 * (mặc định 2000). Không in MST/dữ liệu.
 */
class Hd8CandidateIndexIT {
    static HikariDataSource ds;
    static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() {
        ds = ItInfra.doris();
        jdbc = new JdbcTemplate(ds);
        jdbc.setQueryTimeout(900);
    }

    @AfterAll
    static void close() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void oldPerBatchAndNewCandidateIndex_returnSameRecords_onDevBuyer() {
        List<Map<String, Object>> top = jdbc.queryForList("SELECT MST FROM datahub_hddt.HD_MST_LIST WHERE CHIEU = 'MUA_VAO'"
                + " AND IS_ELIGIBLE = 1 GROUP BY MST ORDER BY COUNT(*) DESC LIMIT 1");
        Assumptions.assumeFalse(top.isEmpty(), "DEV không có HĐ mua vào");
        String buyer = (String) top.get(0).get("MST");
        String dayEnv = ItInfra.env("IT_HD8_DAY");
        LocalDate day = dayEnv != null ? LocalDate.parse(dayEnv) : jdbc.queryForObject("SELECT TDLAP FROM datahub_hddt.HD_MST_LIST"
                + " WHERE MST = ? AND CHIEU = 'MUA_VAO' AND IS_ELIGIBLE = 1 GROUP BY TDLAP ORDER BY COUNT(*) DESC LIMIT 1",
                LocalDate.class, buyer);
        long inRange = jdbc.queryForObject("SELECT COUNT(*) FROM datahub_hddt.HD_MST_LIST WHERE MST = ? AND CHIEU = 'MUA_VAO'"
                + " AND IS_ELIGIBLE = 1 AND TDLAP = ?", Long.class, buyer, day);

        Set<InvoiceKey> keys = new LinkedHashSet<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT l.NBMST, h.KHMSHDON, l.KHHDON, l.SHDON FROM datahub_hddt.HD_MST_LIST l"
                + " JOIN datahub_hddt.HOADON_HDR h ON h.TDLAP = l.TDLAP AND h.CQT = l.CQT AND h.LOAI_HD = l.LOAI_HD AND h.ID = l.ID"
                + " WHERE l.MST = ? AND l.CHIEU = 'MUA_VAO' AND l.IS_ELIGIBLE = 1 AND l.TDLAP = ? AND h.TDLAP = ? LIMIT ?",
                buyer, day, day, ItInfra.env("IT_HD8_KEYS") == null ? 2000 : Integer.parseInt(ItInfra.env("IT_HD8_KEYS")))) {
            InvoiceKey k = new InvoiceKey((String) r.get("NBMST"), ((Number) r.get("KHMSHDON")).intValue(), (String) r.get("KHHDON"),
                    ((Number) r.get("SHDON")).longValue());
            keys.add(k);
            if (keys.size() % 20 == 0) {
                keys.add(new InvoiceKey(k.nbmst(), k.khmshdon(), k.khhdon(), k.shdon() + 900_000_000L)); // không tồn tại
                keys.add(new InvoiceKey(k.nbmst(), k.khmshdon() == 1 ? 5 : 1, k.khhdon(), k.shdon()));    // khác mẫu số
            }
        }
        Assumptions.assumeTrue(keys.size() > 100, "quá ít HĐ mẫu");
        HddtProperties p = DorisDevIT.props("hddt", HddtProperties.Export.Engine.JDBC_STREAM, null);
        DorisInvoiceLookupAdapter adapter = new DorisInvoiceLookupAdapter(jdbc, p);
        List<List<InvoiceKey>> batches = new ArrayList<>();
        List<InvoiceKey> all = new ArrayList<>(keys);
        for (int i = 0; i < all.size(); i += 500) {
            batches.add(all.subList(i, Math.min(all.size(), i + 500)));
        }

        long t0 = System.nanoTime();
        List<InvoiceRecord> old = new ArrayList<>();
        for (List<InvoiceKey> b : batches) {
            old.addAll(adapter.findPurchases(buyer, day, day, b));
        }
        long t1 = System.nanoTime();
        long counted = adapter.countPurchaseCandidates(buyer, day, day);
        long tCount = (System.nanoTime() - t1) / 1_000_000;
        assertThat(counted).isEqualTo(inRange);
        t1 = System.nanoTime();
        Set<Long> fps = new HashSet<>();
        keys.forEach(k -> fps.add(k.lookupFingerprint()));
        PurchaseCandidates idx = adapter.indexPurchases(buyer, day, day, fps::contains);
        long t2 = System.nanoTime();
        List<InvoiceRecord> neu = new ArrayList<>();
        for (List<InvoiceKey> b : batches) {
            neu.addAll(adapter.fetchPurchases(buyer, idx, b));
        }
        long t3 = System.nanoTime();

        assertThat(neu).hasSameSizeAs(old);
        assertThat(new HashSet<>(neu)).isEqualTo(new HashSet<>(old));
        Map<InvoiceKey, Integer> perKeyOld = new HashMap<>();
        old.forEach(r -> perKeyOld.merge(r.key(), 1, Integer::sum));
        Map<InvoiceKey, Integer> perKeyNew = new HashMap<>();
        neu.forEach(r -> perKeyNew.merge(r.key(), 1, Integer::sum));
        assertThat(perKeyNew).isEqualTo(perKeyOld); // NOT_FOUND / AMBIGUOUS giống nhau
        assertThat(old).isNotEmpty();
        System.out.printf("HD8 DEV: %d HĐ mua vào trong ngày, %d khóa (%d lô); tìm thấy %d (%d khóa)%n", inRange, keys.size(),
                batches.size(), old.size(), perKeyOld.size());
        System.out.printf("HD8 DEV: cũ %d ms (%d truy vấn LIST⨝HDR); mới: đếm %d ms, chỉ mục %d ms (%d ứng viên) + tra %d ms;"
                + " quy tắc ngưỡng 20 chọn %s%n", (t1 - t0 - tCount * 1_000_000L) / 1_000_000, batches.size(), tCount,
                (t2 - t1) / 1_000_000, idx.size(), (t3 - t2) / 1_000_000, counted <= keys.size() * 20L ? "CHỈ MỤC" : "THEO LÔ");
    }
}
