package vn.teca.hddt.dataapi.adapter.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

/**
 * Swagger UI tự sinh X-Request-Id: ô X-Request-Id để nguyên giá trị mẫu ({@link OpenApiConfig#REQUEST_ID_EXAMPLE}) ⇒
 * mỗi lần bấm "Execute" trình duyệt thay bằng một UUID mới, nên gọi thử HD1/HD5/HD8/TMS không bị trả lại job cũ hay
 * 409 HDDT-409-IDEMPOTENCY (SI-36). Nhập UUID khác ⇒ giữ nguyên (để thử gửi lại cùng X-Request-Id).
 *
 * <p>Chỉ sửa {@code swagger-initializer.js} mà springdoc phục vụ (thêm {@code requestInterceptor} vào cấu hình
 * SwaggerUIBundle) — không đụng tới API, kiểm tra header thật vẫn ở {@code ClientAuthenticationFilter}. Bean này thay
 * {@code indexPageTransformer} mặc định của springdoc ({@code @ConditionalOnMissingBean}) và vẫn chạy đủ các biến đổi
 * mặc định của nó trước.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {"springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"}, matchIfMissing = true)
public class SwaggerUiConfig {
    private static final Logger log = LoggerFactory.getLogger(SwaggerUiConfig.class);

    /** Dòng mở cấu hình trong swagger-initializer.js (giữ nguyên sau các biến đổi của springdoc 1.8.0). */
    static final String ANCHOR = "window.ui = SwaggerUIBundle({";

    /**
     * Đoạn JS (ES5) chèn ngay sau {@link #ANCHOR}. {@code crypto.randomUUID} chỉ có trong secure context (HTTPS /
     * localhost) — lab chạy http://IP nên dự phòng bằng {@code crypto.getRandomValues} (UUID v4).
     */
    static final String REQUEST_ID_INTERCEPTOR = "\n"
            + "    // hddt-data-api-service: X-Request-Id = gia tri mau => UUID moi cho moi lan Execute (SI-36)\n"
            + "    requestInterceptor: function (req) {\n"
            + "      var placeholder = '" + OpenApiConfig.REQUEST_ID_EXAMPLE + "';\n"
            + "      var newUuid = function () {\n"
            + "        var c = window.crypto;\n"
            + "        if (typeof c.randomUUID === 'function') { return c.randomUUID(); }\n"
            + "        var b = new Uint8Array(16);\n"
            + "        c.getRandomValues(b);\n"
            + "        b[6] = (b[6] & 0x0f) | 0x40;\n"
            + "        b[8] = (b[8] & 0x3f) | 0x80;\n"
            + "        var s = '';\n"
            + "        for (var i = 0; i < 16; i++) {\n"
            + "          s += (b[i] + 0x100).toString(16).substring(1);\n"
            + "          if (i === 3 || i === 5 || i === 7 || i === 9) { s += '-'; }\n"
            + "        }\n"
            + "        return s;\n"
            + "      };\n"
            + "      var h = req.headers || {};\n"
            + "      for (var k in h) {\n"
            + "        if (Object.prototype.hasOwnProperty.call(h, k) && k.toLowerCase() === '"
            + RequestContext.HEADER_REQUEST_ID.toLowerCase() + "'\n"
            + "            && String(h[k]).trim().toLowerCase() === placeholder) {\n"
            + "          h[k] = newUuid();\n"
            + "        }\n"
            + "      }\n"
            + "      return req;\n"
            + "    },";

    @Bean
    public SwaggerIndexTransformer hddtSwaggerIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                                               SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                                               SwaggerUiConfigParameters swaggerUiConfigParameters,
                                                               SwaggerWelcomeCommon swaggerWelcomeCommon,
                                                               ObjectMapperProvider objectMapperProvider) {
        return new RequestIdIndexTransformer(swaggerUiConfig, swaggerUiOAuthProperties, swaggerUiConfigParameters,
                swaggerWelcomeCommon, objectMapperProvider);
    }

    /** Thêm requestInterceptor vào cấu hình SwaggerUIBundle; không tìm thấy chỗ chèn hoặc đã có interceptor ⇒ giữ nguyên. */
    static String injectRequestIdInterceptor(String js) {
        int at = js.indexOf(ANCHOR);
        if (at < 0 || js.contains("requestInterceptor")) {
            log.warn("Swagger UI: không chèn được tự sinh X-Request-Id ({}) — ô X-Request-Id phải tự đổi UUID",
                    at < 0 ? "không thấy '" + ANCHOR + "'" : "đã có requestInterceptor (CSRF?)");
            return js;
        }
        int end = at + ANCHOR.length();
        return js.substring(0, end) + REQUEST_ID_INTERCEPTOR + js.substring(end);
    }

    static final class RequestIdIndexTransformer extends SwaggerIndexPageTransformer {
        RequestIdIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig, SwaggerUiOAuthProperties oauth,
                                  SwaggerUiConfigParameters parameters, SwaggerWelcomeCommon welcome,
                                  ObjectMapperProvider objectMapperProvider) {
            super(swaggerUiConfig, oauth, parameters, welcome, objectMapperProvider);
        }

        @Override
        public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
                throws IOException {
            Resource out = super.transform(request, resource, chain);
            if (!(out instanceof TransformedResource)) {
                return out; // springdoc chỉ biến đổi swagger-initializer.js; tài nguyên khác trả nguyên
            }
            String js = new String(((TransformedResource) out).getByteArray(), StandardCharsets.UTF_8);
            return new TransformedResource(resource, injectRequestIdInterceptor(js).getBytes(StandardCharsets.UTF_8));
        }
    }
}
