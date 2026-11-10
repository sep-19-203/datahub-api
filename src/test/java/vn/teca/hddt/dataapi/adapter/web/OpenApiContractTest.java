package vn.teca.hddt.dataapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** docs/api/openapi.yaml phải khớp DTO thực thi (tên + thứ tự field) và có đủ 7 API với header bắt buộc. */
class OpenApiContractTest {
    static Map<String, Object> doc;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws Exception {
        try (InputStream in = new FileInputStream("docs/api/openapi.yaml")) {
            doc = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    static List<String> props(String schema) {
        return new ArrayList<>(map(map(map(map(doc.get("components")).get("schemas")).get(schema)).get("properties")).keySet());
    }

    /** Java 8 không có record: thứ tự field hợp đồng = thứ tự tham số của constructor duy nhất (biên dịch với -parameters). */
    static List<String> components(Class<?> dto) {
        Constructor<?>[] ctors = dto.getConstructors();
        assertThat(ctors).hasSize(1);
        assertThat(ctors[0].getParameters()).allMatch(Parameter::isNamePresent);
        return Arrays.stream(ctors[0].getParameters()).map(Parameter::getName).collect(Jdk8Compat.toList());
    }

    @Test
    void sevenOperationsWithSpecMethods() {
        Map<String, Object> paths = map(doc.get("paths"));
        assertThat(paths.keySet()).containsExactlyInAnyOrder("/hthue/v1/mua-vao", "/hthue/v1/chi-tiet", "/hthue/v1/ban-ra",
                "/hthue/v1/goi-y", "/hthue/v1/mua-vao/kiem-tra", "/hthue/v1/cap-nhat-hoa-don-hoan-thue",
                "/hthue/v1/job/{jobId}");
        assertThat(map(paths.get("/hthue/v1/chi-tiet"))).containsKey("get");
        assertThat(map(paths.get("/hthue/v1/job/{jobId}"))).containsKey("get");
        for (String p : Jdk8Compat.listOf("/hthue/v1/mua-vao", "/hthue/v1/ban-ra", "/hthue/v1/goi-y", "/hthue/v1/mua-vao/kiem-tra",
                "/hthue/v1/cap-nhat-hoa-don-hoan-thue")) {
            assertThat(map(paths.get(p))).containsOnlyKeys("post");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyOperationDeclaresRequestIdPartnerAndHmacHeaders() {
        // TD-API-40 (09/11): không còn X-Client-Id/X-Client-Secret — bên gọi theo x-partner, chữ ký trục x-hmac
        for (Object path : map(doc.get("paths")).values()) {
            for (Object op : map(path).values()) {
                List<Object> params = (List<Object>) map(op).get("parameters");
                assertThat(params.toString()).contains("#/components/parameters/RequestId",
                        "#/components/parameters/Partner", "#/components/parameters/Hmac")
                        .doesNotContain("ClientId", "ClientSecret");
            }
        }
    }

    @Test
    void schemasMatchImplementedDtos() {
        assertThat(props("PurchaseListRequest")).containsExactlyElementsOf(components(ApiDtos.PurchaseListRequest.class));
        assertThat(props("SalesListRequest")).containsExactlyElementsOf(components(ApiDtos.SalesListRequest.class));
        assertThat(props("DeclarationSuggestionRequest")).containsExactlyElementsOf(components(ApiDtos.DeclarationSuggestionRequest.class));
        assertThat(props("PurchaseVerificationRequest")).containsExactlyElementsOf(components(ApiDtos.PurchaseVerificationRequest.class));
        assertThat(props("RefundUpdateRequest")).containsExactlyElementsOf(components(ApiDtos.RefundUpdateRequest.class));
        assertThat(props("JobStatusData")).containsExactlyElementsOf(components(ApiDtos.JobStatusData.class));
        assertThat(props("JobAckData")).containsExactlyElementsOf(components(ApiDtos.JobAckData.class));
        assertThat(props("InvoiceDetailData")).containsExactlyElementsOf(components(ApiDtos.InvoiceDetailData.class));
        assertThat(props("TaxRate")).containsExactlyElementsOf(components(ApiDtos.TaxRateDto.class));
        assertThat(props("GoodsService")).containsExactlyElementsOf(components(ApiDtos.GoodsDto.class));
        assertThat(props("PurchaseSummary")).containsExactlyElementsOf(components(ApiDtos.PurchaseSummaryDto.class));
        assertThat(props("SalesSummary")).containsExactlyElementsOf(components(ApiDtos.SalesSummaryDto.class));
        assertThat(props("Envelope")).containsExactlyElementsOf(components(ApiResponse.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobStatusEnumMatchesSpec() {
        Map<String, Object> s = map(map(map(doc.get("components")).get("schemas")).get("JobTthai"));
        assertThat((List<String>) s.get("enum")).containsExactly("TIEP_NHAN", "DANG_XU_LY", "HOAN_THANH", "LOI");
    }
}
