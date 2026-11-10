package vn.teca.hddt.dataapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.teca.hddt.dataapi.adapter.web.security.ClientRegistry;
import vn.teca.hddt.dataapi.adapter.web.security.RateLimiter;
import vn.teca.hddt.dataapi.application.detail.InvoiceDetailService;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.export.RealtimeResultWaiter;
import vn.teca.hddt.dataapi.application.job.JobQueryService;
import vn.teca.hddt.dataapi.application.job.JobSubmissionService;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.application.suggest.DeclarationSuggestionService;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.BusinessRuleNotConfirmedException;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.PurchaseTotals;
import vn.teca.hddt.dataapi.domain.hd7.SalesField;
import vn.teca.hddt.dataapi.domain.invoice.GoodsLine;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceDetail;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.invoice.TaxRateLine;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.support.TestProps;

/** Hợp đồng HTTP của 7 API: method/path, header bắt buộc, xác thực/phân quyền, validate, tên trường, mã trạng thái. */
@WebMvcTest(controllers = HthueController.class)
@Import({ClientRegistry.class, RateLimiter.class, ApiContractTest.Beans.class})
class ApiContractTest {

    @TestConfiguration
    static class Beans {
        /**
         * Spring Boot 2.7 không cho khai báo lớp @ConstructorBinding bằng @Bean (sẽ cố bind lại và báo lỗi); sản phẩm của
         * FactoryBean không qua bước bind @ConfigurationProperties ⇒ giữ nguyên giá trị TestProps như bản Spring Boot 3.
         */
        @Bean
        FactoryBean<HddtProperties> hddtProperties() {
            return new AbstractFactoryBean<HddtProperties>() {
                @Override
                public Class<?> getObjectType() {
                    return HddtProperties.class;
                }

                @Override
                protected HddtProperties createInstance() {
                    return TestProps.defaults();
                }
            };
        }

        @Bean
        S3UrlPolicy s3UrlPolicy() {
            return new S3UrlPolicy(Jdk8Compat.listOf("hddt"));
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    static final String RID = "75743f01-c7ce-4dd8-9e9c-9b5c43b28131";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockBean
    JobSubmissionService submissions;
    @MockBean
    JobQueryService jobQueries;
    @MockBean
    InvoiceDetailService detailService;
    @MockBean
    DeclarationSuggestionService suggestionService;
    @MockBean
    TaxpayerStatusPort statuses;
    @MockBean
    RealtimeResultWaiter realtime;

    @BeforeEach
    void statusSourceConfigured() {
        when(statuses.configured()).thenReturn(true);
        when(realtime.awaitIfSmall(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    static Job job(JobType type, JobState state, String url, Long rows) {
        return new Job(UUID.fromString("26cbb6dc-e896-45d9-bbc6-cc1d640d091f"), type, type.defaultCode(), "tthc-hthue",
                RID, "h", "{}", null, state, 1, 3, Instant.parse("2026-10-28T02:52:19Z"), null, null, null, null, null,
                "hddt", "p/", url, null, rows, state == JobState.SUCCEEDED ? "Thành công" : null, null, null, null);
    }

    /** TD-API-40 (09/11): không còn X-Client-Id/X-Client-Secret — bên gọi theo x-partner (ESB tắt trong test). */
    static MockHttpServletRequestBuilder tthc(MockHttpServletRequestBuilder b) {
        return b.header("X-Request-Id", RID).header("x-partner", "TTHC");
    }

    static MockHttpServletRequestBuilder tms(MockHttpServletRequestBuilder b) {
        return b.header("X-Request-Id", RID).header("x-partner", "TMS");
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String lastBody;

    JsonNode call(MockHttpServletRequestBuilder b, int expectedStatus) throws Exception {
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        lastBody = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode n = mapper.readTree(lastBody);
        assertThat(fields(n)).containsExactly("traceId", "status", "code", "errorCode", "message", "data");
        assertThat(n.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(n.get("traceId").asText()).isEqualTo(RID);
        return n;
    }

    static List<String> fields(JsonNode n) {
        List<String> out = new ArrayList<>();
        Iterator<String> it = n.fieldNames();
        it.forEachRemaining(out::add);
        return out;
    }

    static final String HD1_BODY = "{\"nmmst\":\"0100000001\",\"tthang\":\"01/2026\",\"dthang\":\"01/2026\","
            + "\"lbttmsthue\":true,\"lbbhphap\":true}";

    @Test
    void hd1_accepted202_withJobDataFieldsInSpecOrder_andNoTracedId() throws Exception {
        when(submissions.submit(eq(JobType.PURCHASE_LIST), any(), eq(RID), any(), eq("0100000001")))
                .thenReturn(job(JobType.PURCHASE_LIST, JobState.QUEUED, null, null));
        JsonNode n = call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY), 202);
        assertThat(n.get("code").asText()).isEqualTo("OK");
        assertThat(n.get("errorCode").asText()).isEqualTo("0");
        assertThat(n.get("message").asText()).isEqualTo("Thành công");
        assertThat(n.has("tracedId")).isFalse();
        JsonNode d = n.get("data");
        assertThat(fields(d)).containsExactly("jobId", "jobCode", "tthai", "mta", "tdkxuat", "url", "tsdong");
        assertThat(d.get("jobCode").asText()).isEqualTo("hthue.mua-vao");
        assertThat(d.get("tthai").asText()).isEqualTo("TIEP_NHAN");
        assertThat(d.get("tdkxuat").asText()).isEqualTo("2026-10-28 09:52:19");
        assertThat(d.get("url").isNull()).isTrue();
    }

    @Test
    void hd1_smallResult_returnsHoanThanhWithUrlAndTsdongInPostResponse() throws Exception {
        when(submissions.submit(eq(JobType.PURCHASE_LIST), any(), eq(RID), any(), eq("0100000001")))
                .thenReturn(job(JobType.PURCHASE_LIST, JobState.QUEUED, null, null));
        String url = "s3://hddt/export/query-result/26cbb6dc-e896-45d9-bbc6-cc1d640d091f/c852ec478bc8afffb49e7-a82661eec9d995ad_*";
        when(realtime.awaitIfSmall(any(), any())).thenReturn(job(JobType.PURCHASE_LIST, JobState.SUCCEEDED, url, 120L));
        JsonNode d = call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY), 202).get("data");
        assertThat(d.get("tthai").asText()).isEqualTo("HOAN_THANH");
        assertThat(d.get("url").asText()).isEqualTo(url);
        assertThat(d.get("tsdong").asLong()).isEqualTo(120L);
    }

    @Test
    void hd5_usesSellerField_andSalesJobCode() throws Exception {
        when(submissions.submit(eq(JobType.SALES_LIST), any(), eq(RID), any(), eq("0100000002")))
                .thenReturn(job(JobType.SALES_LIST, JobState.QUEUED, null, null));
        JsonNode n = call(json(tthc(post("/hthue/v1/ban-ra")), "{\"nbmst\":\"0100000002\",\"tthang\":\"01/2026\","
                + "\"dthang\":\"03/2026\",\"lbttmsthue\":false,\"lbbhphap\":false}"), 202);
        assertThat(n.at("/data/jobCode").asText()).isEqualTo("hthue.ban-ra");
        assertThat(n.at("/status").asInt()).isEqualTo(202);
        verify(realtime).awaitIfSmall(any(), any()); // SI-06: HD5 cũng trả ngay khi < 10k dòng
    }

    @Test
    void hd5_smallResult_returnsHoanThanhWithUrlAndTsdongInPostResponse() throws Exception {
        when(submissions.submit(eq(JobType.SALES_LIST), any(), eq(RID), any(), eq("0100000002")))
                .thenReturn(job(JobType.SALES_LIST, JobState.QUEUED, null, null));
        String url = "s3://hddt/export/query-result/26cbb6dc-e896-45d9-bbc6-cc1d640d091f/c852ec478bc8afffb49e7-a82661eec9d995ad_*";
        when(realtime.awaitIfSmall(any(), any())).thenReturn(job(JobType.SALES_LIST, JobState.SUCCEEDED, url, 42L));
        JsonNode d = call(json(tthc(post("/hthue/v1/ban-ra")), "{\"nbmst\":\"0100000002\",\"tthang\":\"01/2026\","
                + "\"dthang\":\"01/2026\",\"lbttmsthue\":true,\"lbbhphap\":true}"), 202).get("data");
        assertThat(fields(d)).containsExactly("jobId", "jobCode", "tthai", "mta", "tdkxuat", "url", "tsdong");
        assertThat(d.get("jobCode").asText()).isEqualTo("hthue.ban-ra");
        assertThat(d.get("tthai").asText()).isEqualTo("HOAN_THANH");
        assertThat(d.get("url").asText()).isEqualTo(url);
        assertThat(d.get("tsdong").asLong()).isEqualTo(42L);
    }

    @Test
    void missingRequestId_is400Header_unknownOrMissingPartner_is401_wrongPartnerRole_is403() throws Exception {
        MvcResult noRid = mvc.perform(json(post("/hthue/v1/mua-vao").header("x-partner", "TTHC"), HD1_BODY)).andReturn();
        assertThat(noRid.getResponse().getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(noRid.getResponse().getContentAsString()).get("errorCode").asText())
                .isEqualTo("HDDT-400-HEADER");
        JsonNode bad = call(json(post("/hthue/v1/mua-vao").header("X-Request-Id", RID).header("x-partner", "KHAC"),
                HD1_BODY), 401);
        assertThat(bad.get("errorCode").asText()).isEqualTo("HDDT-401");
        // TestProps không khai default-client ⇒ thiếu x-partner bị 401; X-Client-* không còn tác dụng
        call(json(post("/hthue/v1/mua-vao").header("X-Request-Id", RID).header("X-Client-Id", "tthc-hthue")
                .header("X-Client-Secret", "bat-ky"), HD1_BODY), 401);
        call(json(tms(post("/hthue/v1/mua-vao")), HD1_BODY), 403);
        call(json(tthc(post("/hthue/v1/cap-nhat-hoa-don-hoan-thue")), "{\"s3url\":\"s3://hddt/import/tms/a.csv\"}"), 403);
        verify(submissions, never()).submit(any(), any(), any(), any(), any());
    }

    @Test
    void validation_badMonth_missingFlag_unknownField_reversedRange_mediaType() throws Exception {
        assertThat(call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY.replace("01/2026\",\"dthang", "1/2026\",\"dthang")), 400)
                .get("message").asText()).contains("tthang");
        assertThat(call(json(tthc(post("/hthue/v1/mua-vao")), "{\"nmmst\":\"0100000001\",\"tthang\":\"01/2026\","
                + "\"dthang\":\"01/2026\",\"lbttmsthue\":true}"), 400).get("message").asText()).contains("lbbhphap");
        call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY.replace("\"nmmst\"", "\"nmst\"")), 400);
        assertThat(call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY.replace("\"dthang\":\"01/2026\"",
                "\"dthang\":\"12/2025\"")), 400).get("message").asText()).contains("tthang phải nhỏ hơn");
        call(tthc(post("/hthue/v1/mua-vao")).contentType(MediaType.TEXT_PLAIN).content(HD1_BODY), 415);
        call(json(tthc(post("/hthue/v1/mua-vao")), "{\"nmmst\":\"0100000001\",\"tthang\":\"01/2026\",\"dthang\":\"01/2026\","
                + "\"lbttmsthue\":true,\"lbbhphap\":true,\"x\":\"" + String.join("", java.util.Collections.nCopies(70_000, "a")) + "\"}"), 413);
    }

    @Test
    void mstStatusSourceMissing_is503_forExports() throws Exception {
        when(statuses.configured()).thenReturn(false);
        assertThat(call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY), 503).get("errorCode").asText())
                .isEqualTo("HDDT-503-MST-STATUS");
    }

    @Test
    void idempotencyConflict_is409() throws Exception {
        when(submissions.submit(any(), any(), any(), any(), any())).thenThrow(new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(call(json(tthc(post("/hthue/v1/mua-vao")), HD1_BODY), 409).get("errorCode").asText())
                .isEqualTo("HDDT-409-IDEMPOTENCY");
    }

    @Test
    void hd3_sync200_withNestedTaxArrayAndItems() throws Exception {
        InvoiceHeader h = new InvoiceHeader(LocalDate.of(2026, 10, 28), "0100", "INV", "id1", "0100000002", 1, "C26MAA", 99L,
                LocalDateTime.of(2026, 10, 28, 9, 52, 19), 1, "CÔNG TY TNHH ABC", new BigDecimal("940000.000000"),
                new BigDecimal("75200.000000"), Jdk8Compat.listOf(new TaxRateLine("8%", new BigDecimal("940000"), new BigDecimal("75200"))),
                null, null, null, "Điều chỉnh giảm đơn giá");
        when(detailService.get("0100000002", 1, "C26MAA", 99L, LocalDate.of(2026, 10, 28))).thenReturn(new InvoiceDetail(h,
                "00", Jdk8Compat.listOf(new GoodsLine(1, "Hàng hóa A", new BigDecimal("940000"), "8%", new BigDecimal("75200")))));
        JsonNode n = call(tthc(get("/hthue/v1/chi-tiet").param("nbmst", "0100000002").param("khmshdon", "1")
                .param("khhdon", "C26MAA").param("shdon", "99").param("tdlap", "2026-10-28")), 200);
        JsonNode d = n.get("data");
        assertThat(fields(d)).containsExactly("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "nky", "tthai", "nbten",
                "tgtcthue", "tgtthue", "thttltsuat", "ttmstnban", "bhphap", "bhpldo", "bhpngay", "dshhdvu", "lddctthe");
        assertThat(d.get("lddctthe").asText()).isEqualTo("Điều chỉnh giảm đơn giá"); // API v3 HD3!C51
        assertThat(d.get("khmshdon").isInt()).isTrue();
        assertThat(d.get("tdlap").asText()).isEqualTo("2026-10-28");
        assertThat(d.get("nky").asText()).isEqualTo("2026-10-28 09:52:19");
        // BigDecimal giữ nguyên scale, không dạng mũ (kiểm trên JSON thô vì JsonNode chuẩn hóa số 0 cuối)
        assertThat(lastBody).contains("\"tgtcthue\":940000.000000").contains("\"tgtthue\":75200.000000");
        assertThat(d.get("thttltsuat").isArray()).isTrue();
        assertThat(fields(d.at("/thttltsuat/0"))).containsExactly("tsuat", "thtien", "tthue");
        assertThat(d.at("/thttltsuat/0/tsuat").asText()).isEqualTo("8%");
        assertThat(fields(d.at("/dshhdvu/0"))).containsExactly("ten", "thtcthue", "ltsuat", "tthue");
    }

    @Test
    void hd3_paramValidation_and404_409() throws Exception {
        call(tthc(get("/hthue/v1/chi-tiet").param("nbmst", "0100000002").param("khmshdon", "AB")
                .param("khhdon", "C26MAA").param("shdon", "99").param("tdlap", "2026-10-28")), 400);
        call(tthc(get("/hthue/v1/chi-tiet").param("nbmst", "0100000002").param("khmshdon", "1")
                .param("khhdon", "C26MAA").param("shdon", "99").param("tdlap", "28/10/2026")), 400);
        call(tthc(get("/hthue/v1/chi-tiet").param("nbmst", "0100000002")), 400);
        when(detailService.get(anyString(), anyInt(), anyString(), anyLong(), any()))
                .thenThrow(new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        call(tthc(get("/hthue/v1/chi-tiet").param("nbmst", "0100000002").param("khmshdon", "1")
                .param("khhdon", "C26MAA").param("shdon", "99").param("tdlap", "2026-10-28")), 404);
    }

    @Test
    void hd7_fieldsWithoutSource_areSerializedAsExplicitNull_othersAsPlainNumbers() throws Exception {
        java.util.Map<SalesField, BigDecimal> sales = new java.util.HashMap<>();
        for (SalesField f : SalesField.values()) {
            sales.put(f, BigDecimal.ZERO);
        }
        sales.put(SalesField.TGTBR10, new BigDecimal("941000.000001"));
        sales.put(SalesField.TGTBRKTGTT, null);
        sales.put(SalesField.TGTBRKTPVDC, null);
        when(suggestionService.suggest(any(), any())).thenReturn(new DeclarationSuggestionService.Suggestion(
                new PurchaseTotals(new BigDecimal("10"), new BigDecimal("1"), 2), sales));
        JsonNode n = call(json(tthc(post("/hthue/v1/goi-y")),
                "{\"mst\":\"0100000001\",\"tthang\":\"09/2026\",\"dthang\":\"09/2026\"}"), 200);
        JsonNode bra = n.get("data").get("bra");
        assertThat(bra.has("tgtbrktgtt")).isTrue();
        assertThat(bra.get("tgtbrktgtt").isNull()).isTrue();
        assertThat(bra.get("tgtbrktpvdc").isNull()).isTrue();
        assertThat(bra.get("tgtbr10").decimalValue()).isEqualByComparingTo("941000.000001");
        assertThat(n.get("data").get("mvao").get("slmvao").asLong()).isEqualTo(2);
        // SI-64 (sheet 06/11): lddctthe String, không bắt buộc — chưa có nguồn ⇒ null nhưng vẫn có mặt
        assertThat(n.get("data").has("lddctthe")).isTrue();
        assertThat(n.get("data").get("lddctthe").isNull()).isTrue();
    }

    @Test
    void hd7_blockedMapping_is501_notZeros() throws Exception {
        when(suggestionService.suggest(any(), any())).thenThrow(new BusinessRuleNotConfirmedException(
                BusinessRuleNotConfirmedException.Kind.MAPPING, "SI-09", "Chỉ tiêu HD7 chưa có ánh xạ"));
        JsonNode n = call(json(tthc(post("/hthue/v1/goi-y")),
                "{\"mst\":\"0100000001\",\"tthang\":\"09/2026\",\"dthang\":\"09/2026\"}"), 501);
        assertThat(n.get("errorCode").asText()).isEqualTo("HDDT-501-MAPPING");
        assertThat(n.get("data").isNull()).isTrue();
    }

    @Test
    void hd8_isBlockedUntilRulesConfirmed() throws Exception {
        JsonNode n = call(json(tthc(post("/hthue/v1/mua-vao/kiem-tra")), "{\"nmmst\":\"0100000001\",\"tngay\":\"2026-09-01\","
                + "\"dngay\":\"2026-10-20\",\"s3url\":\"s3://hddt/export/query-result/26cbb6dc/x_*\"}"), 501);
        assertThat(n.get("errorCode").asText()).isEqualTo("HDDT-501-RULE");
    }

    @Test
    void tms_returnsJobAckWithConfiguredStatus_andRejectsForeignBucket() throws Exception {
        when(submissions.submit(eq(JobType.REFUND_UPDATE), any(), eq(RID), any(), eq(null)))
                .thenReturn(job(JobType.REFUND_UPDATE, JobState.QUEUED, null, null));
        JsonNode n = call(json(tms(post("/hthue/v1/cap-nhat-hoa-don-hoan-thue")), "{\"s3url\":\"s3://hddt/import/tms/ds_*\"}"), 200);
        assertThat(fields(n.get("data"))).containsExactly("jobId", "jobCode", "tthai");
        assertThat(n.at("/data/jobCode").asText()).isEqualTo("hthue.cap-nhat");
        call(json(tms(post("/hthue/v1/cap-nhat-hoa-don-hoan-thue")), "{\"s3url\":\"s3://evil/import/tms/ds.csv\"}"), 403);
        call(json(tms(post("/hthue/v1/cap-nhat-hoa-don-hoan-thue")), "{\"s3url\":\"http://169.254.169.254/x\"}"), 400);
    }

    @Test
    void getJob_urlOnlyWhenCompleted() throws Exception {
        String id = "26cbb6dc-e896-45d9-bbc6-cc1d640d091f";
        when(jobQueries.get(eq(id), any())).thenReturn(job(JobType.SALES_LIST, JobState.RUNNING, "s3://hddt/p/x_*", 10L));
        JsonNode running = call(tthc(get("/hthue/v1/job/" + id)), 200);
        assertThat(running.at("/data/tthai").asText()).isEqualTo("DANG_XU_LY");
        assertThat(running.at("/data/url").isNull()).isTrue();
        assertThat(running.at("/data/tsdong").isNull()).isTrue();
        when(jobQueries.get(eq(id), any())).thenReturn(job(JobType.SALES_LIST, JobState.SUCCEEDED, "s3://hddt/p/x_*", 100000L));
        JsonNode done = call(tthc(get("/hthue/v1/job/" + id)), 200);
        assertThat(done.at("/data/tthai").asText()).isEqualTo("HOAN_THANH");
        assertThat(done.at("/data/url").asText()).isEqualTo("s3://hddt/p/x_*");
        assertThat(done.at("/data/tsdong").asLong()).isEqualTo(100000L);
        assertThat(done.at("/data/mta").asText()).isEqualTo("Thành công");
        when(jobQueries.get(eq("other"), any())).thenThrow(new ApiException(ErrorCode.JOB_NOT_FOUND));
        call(tthc(get("/hthue/v1/job/other")), 404);
    }
    @Test
    void trailingSlash_andUnknownPath_are404Envelope_withoutReachingController() throws Exception {
        // Spring 6 không khớp "/" cuối; trên Spring 5.3 phải tắt tường minh (WebMvcConfig) để không vượt qua RoutePermissions
        assertThat(call(json(tms(post("/hthue/v1/mua-vao/")), HD1_BODY), 404).get("errorCode").asText()).isEqualTo("HDDT-404");
        assertThat(call(tthc(get("/hthue/v1/khong-co-api")), 404).get("errorCode").asText()).isEqualTo("HDDT-404");
        verify(submissions, never()).submit(any(), any(), any(), any(), any());
    }
}
