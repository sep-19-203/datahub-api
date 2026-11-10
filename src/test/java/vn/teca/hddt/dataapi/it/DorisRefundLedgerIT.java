package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.RefundLedgerPort.Entry;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisRefundLedgerAdapter;
import vn.teca.hddt.dataapi.support.TestProps;

/**
 * Sổ hoàn trên Doris thật (DEV 4.0.8), database TẠM {@code api_it_sohoan_<rand>} tạo bằng đúng
 * sql/doris/02_hd_so_hoan.sql, xóa sau test: ghi theo khối có label, ghi lại cùng label, ghi lại khác khối (idempotent theo
 * UNIQUE KEY), tra "đã hoàn ở mã công việc khác", đếm đối chiếu. Dữ liệu giả hoàn toàn.
 */
class DorisRefundLedgerIT {
    static HikariDataSource ds;
    static JdbcTemplate jdbc;
    static DorisRefundLedgerAdapter ledger;
    static final String DB = "api_it_sohoan_" + UUID.randomUUID().toString().substring(0, 8);
    static final String MCV = UUID.randomUUID().toString();
    static final LocalDate D = LocalDate.of(2026, 9, 15);

    @BeforeAll
    static void createTable() throws Exception {
        ds = ItInfra.doris();
        jdbc = new JdbcTemplate(ds);
        jdbc.setQueryTimeout(120);
        String ddl;
        try (InputStream in = DorisRefundLedgerIT.class.getResourceAsStream("/sql/doris/02_hd_so_hoan.sql")) {
            ddl = new String(Jdk8Compat.readAllBytes(in), StandardCharsets.UTF_8);
        }
        StringBuilder sql = new StringBuilder();
        for (String line : ddl.split("\n")) {
            if (!line.trim().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        jdbc.execute("CREATE DATABASE IF NOT EXISTS " + DB);
        jdbc.execute(sql.toString().replace("${DB}", DB).replace("${REPLICATION}", "\"replication_num\" = \"1\",").trim()
                .replaceAll(";\\s*$", ""));
        HddtProperties p = TestProps.defaults();
        HddtProperties props = new HddtProperties(p.api(), new HddtProperties.Doris(null, null, null, DB, 4, 120, 7200, 600, false),
                p.dse(), p.s3(), p.mstStatus(), p.hd1(), p.hd5(), p.hd7(), p.hd8(), p.tms(), p.export(), p.worker(), p.security(), Jdk8Compat.mapOf());
        ledger = new DorisRefundLedgerAdapter(jdbc, props);
    }

    @AfterAll
    static void drop() {
        if (jdbc != null) {
            jdbc.execute("DROP DATABASE IF EXISTS " + DB + " FORCE");
        }
        if (ds != null) {
            ds.close();
        }
    }

    static Entry entry(String mcv, long shdon) {
        return new Entry(D, "0100000002", 1, "C26TAA", shdon, mcv, "INV", "0100", "id-" + shdon, "0100000001",
                new BigDecimal("940000.000000"), new BigDecimal("75200.000000"), "[{\"tsuat\":\"8%\"}]", "00", 1,
                LocalDateTime.of(2026, 9, 15, 10, 0), shdon, "s3://hddt/import/tms/ds.csv", String.join("", java.util.Collections.nCopies(64, "a")), "tms-hthue");
    }

    @Test
    void writesIdempotently_findsRefundsOfOtherJobs_andCounts() {
        ledger.write(MCV, 0, Jdk8Compat.listOf(entry(MCV, 1), entry(MCV, 2)));
        assertThat(ledger.countActive(MCV, D, D)).isEqualTo(2);
        ledger.write(MCV, 0, Jdk8Compat.listOf(entry(MCV, 1), entry(MCV, 2)));          // thử lại cùng label: không lỗi, không nhân bản
        ledger.write(MCV, 7, Jdk8Compat.listOf(entry(MCV, 1)));                           // label khác, cùng khóa: ghi đè, không nhân bản
        assertThat(ledger.countActive(MCV, D, D)).isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap("SELECT TTHOAN, MSTHSO, NGUON_MSTHSO, MHSO, TGTTHUE, LOAI_HD FROM "
                + DB + ".HD_SO_HOAN WHERE MCV = ? AND SHDON = 1", MCV);
        assertThat(row.get("TTHOAN")).isEqualTo("DA_HOAN");
        assertThat(row.get("MSTHSO")).isEqualTo("0100000001");
        assertThat(row.get("NGUON_MSTHSO")).isEqualTo("HD_NMMST");
        assertThat(row.get("MHSO")).isNull();
        assertThat((BigDecimal) row.get("TGTTHUE")).isEqualByComparingTo("75200");
        List<DatedKey> keys = Jdk8Compat.listOf(new DatedKey(new InvoiceKey("0100000002", 1, "C26TAA", 1), D),
                new DatedKey(new InvoiceKey("0100000002", 1, "C26TAA", 3), D),
                new DatedKey(new InvoiceKey("0100000002", 1, "C26TAA", 2), D.plusDays(1)));
        String other = UUID.randomUUID().toString();
        assertThat(ledger.findActiveRefunds(keys, other)).containsOnly(org.assertj.core.api.Assertions.entry(new InvoiceKey("0100000002", 1, "C26TAA", 1), MCV));
        assertThat(ledger.findActiveRefunds(keys, MCV)).isEmpty();
    }
}
