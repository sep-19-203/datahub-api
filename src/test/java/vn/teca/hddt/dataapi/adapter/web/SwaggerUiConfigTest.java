package vn.teca.hddt.dataapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.TransformedResource;

/** Swagger UI tự sinh X-Request-Id: chạy biến đổi thật của springdoc 1.8.0 trên swagger-initializer.js của webjar. */
class SwaggerUiConfigTest {

    private static SwaggerUiConfig.RequestIdIndexTransformer transformer() {
        SwaggerUiConfigProperties props = new SwaggerUiConfigProperties();
        SwaggerUiConfigParameters params = new SwaggerUiConfigParameters(props);
        params.setConfigUrl("/v3/api-docs/swagger-config"); // đã có configUrl ⇒ springdoc không cần SwaggerWelcomeCommon
        return new SwaggerUiConfig.RequestIdIndexTransformer(props, new SwaggerUiOAuthProperties(), params, null,
                new ObjectMapperProvider(new SpringDocConfigProperties()));
    }

    private static Resource webjar(String file) throws Exception {
        Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:META-INF/resources/webjars/swagger-ui/*/" + file);
        assertThat(found).as("webjar swagger-ui có " + file).hasSize(1);
        return found[0];
    }

    @Test
    void realInitializer_keepsSpringdocTransformations_andGetsExactlyOneInterceptor() throws Exception {
        Resource out = transformer().transform(new MockHttpServletRequest(), webjar("swagger-initializer.js"), null);

        assertThat(out).isInstanceOf(TransformedResource.class);
        String js = new String(((TransformedResource) out).getByteArray(), StandardCharsets.UTF_8);
        assertThat(js).contains(SwaggerUiConfig.ANCHOR + SwaggerUiConfig.REQUEST_ID_INTERCEPTOR)
                .contains("'" + OpenApiConfig.REQUEST_ID_EXAMPLE + "'")
                .contains("=== 'x-request-id'")
                // biến đổi mặc định của springdoc vẫn chạy (thêm configUrl trỏ về API docs của ứng dụng)
                .contains("\"configUrl\" : \"/v3/api-docs/swagger-config\"");
        assertThat(js.split("requestInterceptor", -1)).hasSize(2);
    }

    @Test
    void otherSwaggerResources_areReturnedUntouched() throws Exception {
        Resource css = webjar("swagger-ui.css");
        assertThat(transformer().transform(new MockHttpServletRequest(), css, null)).isSameAs(css);
    }

    @Test
    void inject_leavesScriptAlone_whenAnchorMissing_orInterceptorAlreadyPresent() {
        String noAnchor = "window.onload = function() { SwaggerUIBundle({ url: '/x' }); };";
        assertThat(SwaggerUiConfig.injectRequestIdInterceptor(noAnchor)).isEqualTo(noAnchor);

        String csrf = SwaggerUiConfig.ANCHOR + "\n requestInterceptor: (request) => request,\n dom_id: '#swagger-ui' });";
        assertThat(SwaggerUiConfig.injectRequestIdInterceptor(csrf)).isEqualTo(csrf);
    }
}
