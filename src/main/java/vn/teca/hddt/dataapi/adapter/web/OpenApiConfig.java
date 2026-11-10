package vn.teca.hddt.dataapi.adapter.web;

import java.util.ArrayList;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import vn.teca.hddt.dataapi.adapter.web.security.ClientRegistry;
import vn.teca.hddt.dataapi.adapter.web.security.RoutePermissions;

/**
 * Khai báo header của {@code /hthue/v1/**} trong OpenAPI để Swagger UI có chỗ nhập (chỉ là tài liệu — kiểm tra thật nằm
 * ở {@code ClientAuthenticationFilter}): nút "Authorize" cho x-partner (bên gọi: TTHC/TMS), x-hmac khi bật ESB HMAC và
 * token gateway APIC khi {@code hddt.security.gateway-token-header} được cấu hình; ô X-Request-Id trên từng API.
 * Không còn X-Client-Id/X-Client-Secret (09/11 — chỉ gọi nội bộ, TD-API-40).
 */
@Configuration
public class OpenApiConfig {
    static final String REQUEST_ID_EXAMPLE = "75743f01-c7ce-4dd8-9e9c-9b5c43b28131";

    @Bean
    public OpenApiCustomiser hthueHeaders(ClientRegistry registry) {
        return openApi -> addHeaders(openApi, registry.gatewayTokenHeader(), registry.partnerHeader(),
                registry.esbHmacHeader());
    }

    /** Gọi nhiều lần vẫn cho cùng kết quả (không nhân đôi tham số/yêu cầu bảo mật). */
    static void addHeaders(OpenAPI openApi, String gatewayTokenHeader) {
        addHeaders(openApi, gatewayTokenHeader, "x-partner", null);
    }

    /**
     * Như {@link #addHeaders(OpenAPI, String)}; {@code esbHmacHeader} khác null (bật ESB HMAC, TD-API-40) ⇒ thêm ô x-hmac,
     * x-partner thành bắt buộc (như tthc-sharing) và X-Request-Id thành tùy chọn (ô để trống thì Swagger không gửi). Chữ ký
     * đổi theo ngày, tính ngoài trình duyệt (khóa không đưa vào Swagger), vd
     * {@code printf '%s' "$(TZ=Asia/Ho_Chi_Minh date +%Y%m%d).TTHC" | openssl dgst -sha256 -hmac "$ESB_HMAC_KEY"}.
     */
    static void addHeaders(OpenAPI openApi, String gatewayTokenHeader, String partnerHeader, String esbHmacHeader) {
        boolean esb = esbHmacHeader != null;
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("hddt-data-api-service").version("v1")
                    .description("API dữ liệu HĐĐT phục vụ hoàn thuế (TTHC/TMS gọi nội bộ qua trục ESB). Bấm \"Authorize\" "
                            + "để nhập x-partner (TTHC hoặc TMS)" + (esb ? " và x-hmac" : "") + " trước khi gọi thử."));
        }
        if (openApi.getComponents() == null) {
            openApi.components(new Components());
        }
        Components components = openApi.getComponents();
        SecurityRequirement requirement = new SecurityRequirement();
        addScheme(components, requirement, partnerHeader, esb
                ? "Mã bên gọi: TTHC (HD1/HD3/HD5/HD7/HD8/job) hoặc TMS (cập nhật hoàn thuế/job) — bắt buộc, ký bằng x-hmac."
                : "Mã bên gọi: TTHC (HD1/HD3/HD5/HD7/HD8/job) hoặc TMS (cập nhật hoàn thuế/job). Để trống ⇒ client mặc định.");
        if (gatewayTokenHeader != null) {
            addScheme(components, requirement, gatewayTokenHeader, "Token gateway APIC (bắt buộc khi đã cấu hình).");
        }
        if (esb) {
            addScheme(components, requirement, esbHmacHeader,
                    "HMAC-SHA256 (hex) của 'yyyyMMdd.<mã đối tác>' (ngày giờ VN) với khóa ESB — như hddt-tthc-sharing.");
        }
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith(RoutePermissions.BASE + "/")) {
                return;
            }
            for (Operation op : item.readOperations()) {
                if (op.getSecurity() == null || !op.getSecurity().contains(requirement)) {
                    op.addSecurityItem(requirement);
                }
                if (!hasHeader(op, RequestContext.HEADER_REQUEST_ID)) {
                    List<Parameter> params = new ArrayList<>();
                    params.add(requestIdParameter(!esb));
                    if (op.getParameters() != null) {
                        params.addAll(op.getParameters());
                    }
                    op.setParameters(params);
                }
            }
        });
    }

    private static void addScheme(Components components, SecurityRequirement requirement, String header,
                                  String description) {
        components.addSecuritySchemes(header, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER).name(header).description(description));
        requirement.addList(header);
    }

    private static boolean hasHeader(Operation op, String name) {
        if (op.getParameters() == null) {
            return false;
        }
        for (Parameter p : op.getParameters()) {
            if ("header".equals(p.getIn()) && name.equalsIgnoreCase(p.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Parameter requestIdParameter(boolean required) {
        return new HeaderParameter().name(RequestContext.HEADER_REQUEST_ID).required(required)
                .description((required ? "" : "Tùy chọn khi bật ESB HMAC (không gửi ⇒ server tự sinh, không idempotency). ")
                        + "UUID của lần gọi, dùng làm traceId. Với API tạo job (HD1, HD5, HD8, TMS) đây là khóa "
                        + "idempotency cùng bên gọi x-partner (SI-36): gửi lại cùng X-Request-Id + cùng nội dung ⇒ trả lại job "
                        + "cũ; khác nội dung ⇒ 409 HDDT-409-IDEMPOTENCY ⇒ đổi UUID mới. Trên Swagger UI: để nguyên giá trị mẫu "
                        + "thì mỗi lần Execute tự thay bằng UUID mới (xem trong lệnh curl); nhập UUID khác thì giữ nguyên "
                        + "(dùng để thử gửi lại).")
                .schema(new StringSchema().format("uuid"))
                .example(REQUEST_ID_EXAMPLE);
    }
}
