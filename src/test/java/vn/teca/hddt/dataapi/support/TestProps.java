package vn.teca.hddt.dataapi.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.teca.hddt.dataapi.application.security.ApiPermission;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy.UnassignedCodePolicy;

/** Dựng HddtProperties cho unit test (giá trị giả, không có credential thật). */
public final class TestProps {
    private TestProps() {
    }

    public static HddtProperties.S3 s3() {
        return new HddtProperties.S3("http://minio.test:9000", "us-east-1", "ak-test", "sk-test", true, "hddt",
                "export/query-result", "export/hd8-result", "internal/jobs", Jdk8Compat.listOf("hddt"),
                Jdk8Compat.listOf("import/tthc/", "export/query-result/"), Jdk8Compat.listOf("import/tms/"), null, null, null);
    }

    public static HddtProperties.Dse dse() {
        return new HddtProperties.Dse(Jdk8Compat.listOf("127.0.0.1:9042"), "DC1", null, null, "datahub_hddt",
                "{'class': 'NetworkTopologyStrategy', 'DC1': '1'}", false, 3600, 8, Duration.ofSeconds(10), Duration.ofSeconds(10));
    }

    /** Ánh xạ bán ra 05/11 (giống application.yml): 8% không ánh xạ (SI-56), [34a] = ktpvdc (SI-58). */
    public static Map<String, List<String>> fullMapping() {
        return Jdk8Compat.mapOf("tgtbr0", Jdk8Compat.listOf("0%"), "tgtbr5", Jdk8Compat.listOf("5%"), "tgttbr5", Jdk8Compat.listOf("5%"),
                "tgtbr10", Jdk8Compat.listOf("10%"), "tgttbr10", Jdk8Compat.listOf("10%"), "tgtbrkct", Jdk8Compat.listOf("kct"),
                "tgtbrkkknt", Jdk8Compat.listOf("kkknt"), "tgtbrktgtt", Jdk8Compat.listOf(), "tgtbrktpvdc", Jdk8Compat.listOf("ktpvdc"));
    }

    public static HddtProperties props(String mstStatusSql, boolean hd8Confirmed, HddtProperties.Tms.ApplyMode tmsMode,
                                       Map<String, List<String>> braMapping) {
        return new HddtProperties(
                new HddtProperties.Api(12, 200, 65536, 20, 3),
                new HddtProperties.Doris(null, null, null, "datahub_hddt", 4, 30, 7200, 600, false),
                dse(),
                s3(),
                new HddtProperties.MstStatus(mstStatusSql, Jdk8Compat.listOf("03", "05", "06")),
                new HddtProperties.Hd1(true, 10_000, Duration.ofMillis(300), Duration.ofMillis(10)),
                new HddtProperties.Hd5(true, 10_000, Duration.ofMillis(300), Duration.ofMillis(10)),
                hd7(braMapping, UnassignedCodePolicy.IGNORE),
                new HddtProperties.Hd8(hd8Confirmed, 2, 1000, 10, 1_000_000, 366,
                        Jdk8Compat.listOf("tgtcthue", "tgtthue"), BigDecimal.ZERO, 50, true, 20),
                new HddtProperties.Tms(tmsMode, 2, 1000, 10, 1_000_000, "HD_SO_HOAN", 2),
                new HddtProperties.Export(HddtProperties.Export.Engine.OUTFILE, 1024 * 1024, 7, false, 1_000_000, "_SUCCESS"),
                new HddtProperties.Worker(true, 2, Duration.ofMinutes(2), Duration.ofSeconds(1), 3,
                        Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofSeconds(5), Jdk8Compat.mapOf()),
                security(null, null),
                Jdk8Compat.mapOf());
    }

    /**
     * Như application.yml: TTHC → tthc-hthue, TMS → tms-hthue (không còn secret, TD-API-40). {@code esb} null ⇒ tắt ESB
     * (không xác thực); {@code defaultClient} null ⇒ thiếu x-partner bị 401.
     */
    public static HddtProperties.Security security(HddtProperties.Security.Esb esb, String defaultClient) {
        return new HddtProperties.Security(Jdk8Compat.listOf(
                new HddtProperties.Security.Client("tthc-hthue", Jdk8Compat.setOf(ApiPermission.PURCHASE_LIST,
                        ApiPermission.INVOICE_DETAIL, ApiPermission.SALES_LIST, ApiPermission.DECLARATION_SUGGEST,
                        ApiPermission.PURCHASE_VERIFY, ApiPermission.JOB_READ), true, Jdk8Compat.setOf(), 1000, 1000),
                new HddtProperties.Security.Client("tms-hthue",
                        Jdk8Compat.setOf(ApiPermission.REFUND_UPDATE, ApiPermission.JOB_READ), true, Jdk8Compat.setOf(), 1000, 1000),
                new HddtProperties.Security.Client("noi-bo", Jdk8Compat.setOf(ApiPermission.values()), true,
                        Jdk8Compat.setOf(), 1000, 1000)),
                null, null, esb, Jdk8Compat.mapOf("TTHC", "tthc-hthue", "tms", "tms-hthue"), defaultClient);
    }

    /**
     * Cấu hình HD7 như application.yml (05/11): R1 bật, [23]/[24] = 0/5/10%, suy thuế suất header 10/8/5%, kỳ theo
     * ngày lập (R3/R4), quy đổi ngoại tệ (R7), HĐ điều chỉnh thiếu thuế suất = 0 (D3).
     */
    public static HddtProperties.Hd7 hd7(Map<String, List<String>> braMapping, UnassignedCodePolicy policy) {
        return hd7(braMapping, policy, true, HddtProperties.Hd7.PeriodBasis.NGAY_LAP, true, true);
    }

    public static HddtProperties.Hd7 hd7(Map<String, List<String>> braMapping, UnassignedCodePolicy policy,
                                         boolean formRule, HddtProperties.Hd7.PeriodBasis basis, boolean fx,
                                         boolean adjustmentZero) {
        return new HddtProperties.Hd7(12, formRule, Jdk8Compat.listOf("0%", "5%", "10%"), braMapping, policy,
                Jdk8Compat.listOf("10%", "8%", "5%"), BigDecimal.ONE, new BigDecimal("0.0001"), basis, fx,
                adjustmentZero, HddtProperties.Hd7.SingleRateSource.LINES);
    }

    /** Thay riêng cấu hình HD7 của {@link #defaults()}. */
    public static HddtProperties withHd7(HddtProperties.Hd7 hd7) {
        HddtProperties b = defaults();
        return new HddtProperties(b.api(), b.doris(), b.dse(), b.s3(), b.mstStatus(), b.hd1(), b.hd5(), hd7, b.hd8(),
                b.tms(), b.export(), b.worker(), b.security(), b.jobCodes());
    }

    public static HddtProperties defaults() {
        return props("SELECT MST, MST_CU, TRANG_THAI FROM test.NNT_STATUS", false, HddtProperties.Tms.ApplyMode.BLOCKED, fullMapping());
    }

    /** Bản sao {@code p} với hddt.hd8.candidate-index = {@code on} (DB-P1-02: so đường mới / cũ). */
    public static HddtProperties withHd8CandidateIndex(HddtProperties p, boolean on) {
        return withHd8CandidateIndex(p, on, p.hd8().indexMaxCandidatesPerKey());
    }

    /** … và ngưỡng index-max-candidates-per-key. */
    public static HddtProperties withHd8CandidateIndex(HddtProperties p, boolean on, int maxCandidatesPerKey) {
        HddtProperties.Hd8 h = p.hd8();
        HddtProperties.Hd8 h2 = new HddtProperties.Hd8(h.rulesConfirmed(), h.batchSize(), h.maxRows(), h.maxObjects(),
                h.maxTotalBytes(), h.maxDays(), h.compareFields(), h.moneyTolerance(), h.maxReportedErrors(), on,
                maxCandidatesPerKey);
        return new HddtProperties(p.api(), p.doris(), p.dse(), p.s3(), p.mstStatus(), p.hd1(), p.hd5(), p.hd7(), h2, p.tms(),
                p.export(), p.worker(), p.security(), p.jobCodes());
    }
}
