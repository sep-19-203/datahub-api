package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import vn.teca.hddt.dataapi.application.csv.CsvStreams;
import vn.teca.hddt.dataapi.application.export.ListExportHandler;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisExportAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisInvoiceLookupAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisInvoiceQueryAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisSummaryAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisTables;
import vn.teca.hddt.dataapi.infrastructure.doris.DorisTaxpayerStatusAdapter;
import vn.teca.hddt.dataapi.infrastructure.doris.ExportSqlBuilder;
import vn.teca.hddt.dataapi.infrastructure.s3.S3ObjectStorageAdapter;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;
import vn.teca.hddt.dataapi.support.TestProps;

/**
 * Doris thật (DEV 4.0.8, CHỈ ĐỌC datahub_hddt): kiểm cú pháp + kế hoạch mọi câu SQL, HD3 trên hóa đơn DEV có sẵn,
 * kết xuất HD5 end-to-end (JDBC_STREAM → MinIO) và tùy chọn OUTFILE (IT_DORIS_OUTFILE_S3_ENDPOINT = endpoint MinIO mà
 * BE Doris truy cập được). Không in dữ liệu hóa đơn/MST ra log.
 */
class DorisDevIT {
    static HikariDataSource ds;
    static JdbcTemplate jdbc;
    static final String STATUS_SQL = "SELECT '0100000002' AS MST, NULL AS MST_CU, '00' AS TRANG_THAI";

    @BeforeAll
    static void connect() {
        ds = ItInfra.doris();
        jdbc = new JdbcTemplate(ds);
        jdbc.setQueryTimeout(120);
    }

    @AfterAll
    static void close() {
        if (ds != null) {
            ds.close();
        }
    }

    static HddtProperties props(String bucket, HddtProperties.Export.Engine engine, String dorisS3Endpoint) {
        HddtProperties p = TestProps.defaults();
        String endpoint = ItInfra.env("IT_S3_ENDPOINT");
        HddtProperties.S3 s3 = new HddtProperties.S3(endpoint, "us-east-1", ItInfra.env("IT_S3_ACCESS_KEY"),
                ItInfra.env("IT_S3_SECRET_KEY"), true, bucket, "export/query-result", "export/hd8-result", "internal/jobs",
                Jdk8Compat.listOf(bucket), Jdk8Compat.listOf("import/tthc/"), Jdk8Compat.listOf("import/tms/"), dorisS3Endpoint, null, null);
        return new HddtProperties(p.api(), new HddtProperties.Doris(null, null, null, "datahub_hddt", 4, 120, 7200, 600, false),
                p.dse(), s3, new HddtProperties.MstStatus(STATUS_SQL, Jdk8Compat.listOf("03", "05", "06")), p.hd1(), p.hd5(), p.hd7(), p.hd8(), p.tms(),
                new HddtProperties.Export(engine, 64L * 1024 * 1024, 7, false, 1_000_000, "_SUCCESS"),
                p.worker(), p.security(), Jdk8Compat.mapOf());
    }

    @Test
    void everyAdapterQueryParsesAndRunsOnDoris408_withUnknownKeys() {
        HddtProperties p = props("hddt", HddtProperties.Export.Engine.OUTFILE, null);
        LocalDate d = LocalDate.of(2026, 9, 15);
        DorisInvoiceQueryAdapter q = new DorisInvoiceQueryAdapter(jdbc, p);
        assertThat(q.findHeaders("0100000099", 1, Jdk8Compat.setOf("C26TAA", "C26TĐA"), 1, d, 5)).isEmpty();
        assertThat(q.findGoodsLines(new InvoiceHeader(d, "0100", "INV", "khong-co", null, null, null, null, null, null,
                null, null, null, Jdk8Compat.listOf(), null, null, null, null))).isEmpty();
        DorisSummaryAdapter s = new DorisSummaryAdapter(jdbc, p);
        MonthRange r = MonthRange.parse("01/2026", "12/2026", 12);
        RateBreakdown purchases = s.breakdown(Direction.MUA_VAO, "0100000099", r);
        assertThat(purchases.invoiceCount()).isZero();
        assertThat(purchases.byRate()).isEmpty();
        RateBreakdown sales = s.breakdown(Direction.BAN_RA, "0100000099", r);
        assertThat(sales.byRate()).isEmpty();   // cả truy vấn THTTLTSUAT lẫn dòng hàng DTL chạy được, không có HĐ
        assertThat(sales.invoiceCount()).isZero();
        DorisInvoiceLookupAdapter l = new DorisInvoiceLookupAdapter(jdbc, p);
        InvoiceKey k = new InvoiceKey("0100000099", 1, "C26TAA", 1);
        assertThat(l.findPurchases("0100000098", d.withDayOfMonth(1), d, Jdk8Compat.listOf(k))).isEmpty();
        assertThat(l.findRefundTargets(Jdk8Compat.listOf(new DatedKey(k, d)))).isEmpty();
        DorisTaxpayerStatusAdapter st = new DorisTaxpayerStatusAdapter(jdbc, p);
        assertThat(st.findStatus("0100000002")).contains("00");
        assertThat(st.findStatuses(Jdk8Compat.listOf("0100000002", "0100000099"))).containsOnly(org.assertj.core.api.Assertions.entry("0100000002", "00"));
    }

    @Test
    void exportSqlHasValidPlan_withPartitionAndBucketPruning_forBothDirections() {
        Map<String, Object> inv = devInvoice();
        LocalDate ky = ((java.sql.Date) inv.get("KY")).toLocalDate();
        String month = String.format("%02d/%d", ky.getMonthValue(), ky.getYear());
        HddtProperties p = props("hddt", HddtProperties.Export.Engine.OUTFILE, null);
        ExportSqlBuilder b = new ExportSqlBuilder(new DorisTables("datahub_hddt"), p.mstStatus());
        for (vn.teca.hddt.dataapi.domain.invoice.Direction dir : vn.teca.hddt.dataapi.domain.invoice.Direction.values()) {
            vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec spec = new vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec(UUID.randomUUID(), 1, dir,
                    (String) inv.get("MST"), MonthRange.parse(month, month, 12), true, Jdk8Compat.listOf("03", "05", "06"), true,
                    "hddt", "x/");
            ExportSqlBuilder.BuiltSql q = b.select(spec, 600, false, dir == vn.teca.hddt.dataapi.domain.invoice.Direction.MUA_VAO
                    ? jdbc.query(b.purchaseTdlapBounds(spec).sql(), (org.springframework.jdbc.core.ResultSetExtractor<ExportSqlBuilder.TdlapBounds>) rs -> {
                        rs.next();
                        LocalDate mn = rs.getObject(1, LocalDate.class);
                        return mn == null ? null : new ExportSqlBuilder.TdlapBounds(mn, rs.getObject(2, LocalDate.class));
                    }, b.purchaseTdlapBounds(spec).args().toArray()) : null);
            String plan = String.join("\n", jdbc.queryForList("EXPLAIN " + q.sql(), String.class, q.args().toArray()));
            assertThat(plan).contains("HD_MST_LIST").contains("HOADON_HDR").contains("partitions=");
            new java.io.BufferedReader(new java.io.StringReader(plan)).lines().filter(l -> l.contains("TABLE:") || l.contains("partitions=") || l.contains("tablets="))
                    .map(String::trim).forEach(l -> System.out.println("PLAN[" + dir + "] " + l));
            assertThat(jdbc.queryForList(q.sql(), String.class, q.args().toArray())).isNotNull();
        }
    }

    /** Hóa đơn bán ra có sẵn trên DEV (không in giá trị). */
    static Map<String, Object> devInvoice() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT l.MST, l.KY, l.TDLAP, l.SHDON, l.KHHDON, h.KHMSHDON"
                + " FROM datahub_hddt.HD_MST_LIST l JOIN datahub_hddt.HOADON_HDR h ON " + DorisTables.LIST_HDR_JOIN
                + " WHERE l.CHIEU = 'BAN_RA' LIMIT 1");
        Assumptions.assumeFalse(rows.isEmpty(), "DEV không có hóa đơn nào trong HD_MST_LIST");
        return rows.get(0);
    }

    @Test
    void hd3_findsExistingDevInvoice_exactlyOnce_andReadsDetailInOneQuery() {
        Map<String, Object> inv = devInvoice();
        HddtProperties p = props("hddt", HddtProperties.Export.Engine.OUTFILE, null);
        DorisInvoiceQueryAdapter q = new DorisInvoiceQueryAdapter(jdbc, p);
        LocalDate tdlap = ((java.sql.Date) inv.get("TDLAP")).toLocalDate();
        List<InvoiceHeader> h = q.findHeaders((String) inv.get("MST"), ((Number) inv.get("KHMSHDON")).intValue(),
                Jdk8Compat.setOf((String) inv.get("KHHDON")), ((Number) inv.get("SHDON")).longValue(), tdlap, 5);
        assertThat(h).hasSize(1);
        assertThat(h.get(0).tdlap()).isEqualTo(tdlap);
        assertThat(h.get(0).thttltsuat()).isNotNull();
        assertThat(q.findGoodsLines(h.get(0))).isNotNull();
    }

    Completion runExport(HddtProperties p, S3Client s3, Map<String, Object> inv) throws Exception {
        S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(s3);
        JdbcTemplate exportJdbc = new JdbcTemplate(ds);
        exportJdbc.setQueryTimeout(600);
        DorisExportAdapter exporter = new DorisExportAdapter(exportJdbc, storage, p);
        DorisTaxpayerStatusAdapter statuses = new DorisTaxpayerStatusAdapter(jdbc, p);
        LocalDate ky = ((java.sql.Date) inv.get("KY")).toLocalDate();
        String month = String.format("%02d/%d", ky.getMonthValue(), ky.getYear());
        InMemoryJobRepository repo = new InMemoryJobRepository();
        UUID id = UUID.randomUUID();
        repo.insertIfAbsent(new NewJob(id, JobType.SALES_LIST, "hthue.ban-ra", "c", "r", "h", JobPayloads.toJson(
                new JobPayloads.ListExportParams("BAN_RA", (String) inv.get("MST"), month, month, false, false)), null, 3));
        Job j = repo.claimNext("it", Jdk8Compat.listOf(JobType.SALES_LIST), Duration.ofMinutes(5)).get();
        return new ListExportHandler(exporter, storage, statuses, p).execute(new JobContext(j, repo, "it", Duration.ofMinutes(5)));
    }

    long expectedRows(Map<String, Object> inv) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM datahub_hddt.HD_MST_LIST WHERE CHIEU = 'BAN_RA' AND MST = ?"
                + " AND KY = ? AND IS_ELIGIBLE = 1", Long.class, inv.get("MST"), inv.get("KY"));
        return n == null ? 0 : n;
    }

    /** MinIO dùng chung: xóa object + bucket tạm của test. */
    static void dropBucket(S3Client s3, String bucket) {
        S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(s3);
        List<StoredObject> all = storage.list(bucket, "", 100_000);
        if (!all.isEmpty()) {
            storage.delete(bucket, all.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
        }
        s3.deleteBucket(b -> b.bucket(bucket));
    }

    void assertCsv(S3Client s3, String bucket, Completion done, int columns) throws Exception {
        S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(s3);
        String keyPrefix = done.resultUrl().substring(("s3://" + bucket + "/").length(), done.resultUrl().length() - 1);
        long records = 0;
        for (StoredObject o : storage.list(bucket, keyPrefix, 100)) {
            try (InputStream in = storage.open(bucket, o.key()); CSVParser parser = CsvStreams.open(in)) {
                for (CSVRecord rec : parser) {
                    assertThat(rec.size()).isEqualTo(columns);
                    records++;
                }
            }
        }
        assertThat(records).isEqualTo(done.rowCount());
    }

    @Test
    void hd5_exportEndToEnd_jdbcStream_toMinio_verifiedAndCounted() throws Exception {
        Assumptions.assumeTrue(ItInfra.env("IT_S3_ENDPOINT") != null, "cần IT_S3_ENDPOINT");
        Map<String, Object> inv = devInvoice();
        String bucket = "hddt-it-exp-" + UUID.randomUUID().toString().substring(0, 6);
        try (S3Client s3 = ItInfra.s3()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            try {
                Completion done = runExport(props(bucket, HddtProperties.Export.Engine.JDBC_STREAM, null), s3, inv);
                assertThat(done.rowCount()).isEqualTo(expectedRows(inv));
                assertThat(done.resultUrl()).startsWith("s3://" + bucket + "/export/query-result/").endsWith("_*");
                assertCsv(s3, bucket, done, 18);
            } finally {
                dropBucket(s3, bucket);
            }
        }
    }

    @Test
    void hd5_exportEndToEnd_outfile_whenBeCanReachS3() throws Exception {
        String dorisS3 = ItInfra.env("IT_DORIS_OUTFILE_S3_ENDPOINT");
        Assumptions.assumeTrue(dorisS3 != null && ItInfra.env("IT_S3_ENDPOINT") != null,
                "cần IT_DORIS_OUTFILE_S3_ENDPOINT (MinIO mà BE Doris truy cập được)");
        Map<String, Object> inv = devInvoice();
        String bucket = "hddt-it-out-" + UUID.randomUUID().toString().substring(0, 6);
        try (S3Client s3 = ItInfra.s3()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            try {
                Completion done = runExport(props(bucket, HddtProperties.Export.Engine.OUTFILE, dorisS3), s3, inv);
                assertThat(done.rowCount()).isEqualTo(expectedRows(inv));
                assertThat(done.resultUrl()).matches("s3://" + bucket + "/export/query-result/[0-9a-f-]+/[0-9a-f]+-[0-9a-f]+_\\*");
                assertCsv(s3, bucket, done, 18);
                System.out.println("OUTFILE OK url=" + done.resultUrl().replaceAll("/[0-9a-f-]{36}/", "/<jobId>/")
                        + " rows=" + done.rowCount());
            } finally {
                dropBucket(s3, bucket);
            }
        }
    }
}
