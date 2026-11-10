package vn.teca.hddt.dataapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Giữ hành vi so khớp đường dẫn như Spring 6 (bản Spring Boot 3 trước migration Java 8): KHÔNG khớp dấu "/" cuối.
 * Spring 5.3 mặc định coi "/hthue/v1/mua-vao/" = "/hthue/v1/mua-vao", trong khi {@code RoutePermissions} so khớp URI
 * tuyệt đối ⇒ nếu bật, request có "/" cuối sẽ vào controller mà không qua kiểm tra quyền.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(false);
    }
}
