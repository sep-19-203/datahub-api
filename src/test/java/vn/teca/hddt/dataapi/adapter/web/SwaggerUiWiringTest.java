package vn.teca.hddt.dataapi.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocConfiguration;
import org.springdoc.core.SpringDocUIConfiguration;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Bean của {@link SwaggerUiConfig} thay được {@code indexPageTransformer} của springdoc và được dùng khi phục vụ Swagger UI. */
@SpringBootTest(classes = SwaggerUiWiringTest.App.class)
@AutoConfigureMockMvc
class SwaggerUiWiringTest {

    @Configuration
    @ImportAutoConfiguration({DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class,
            SwaggerConfig.class, SwaggerUiConfigProperties.class, SwaggerUiConfigParameters.class,
            SpringDocUIConfiguration.class, SwaggerUiOAuthProperties.class})
    @Import(SwaggerUiConfig.class)
    static class App {
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ApplicationContext ctx;

    @Test
    void swaggerInitializer_isServedWithRequestIdInterceptor() throws Exception {
        assertThat(ctx.getBeansOfType(SwaggerIndexTransformer.class)).containsOnlyKeys("hddtSwaggerIndexTransformer");
        mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(SwaggerUiConfig.ANCHOR + SwaggerUiConfig.REQUEST_ID_INTERCEPTOR)))
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
    }
}
