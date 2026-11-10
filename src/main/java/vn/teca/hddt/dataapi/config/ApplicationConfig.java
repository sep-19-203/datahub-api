package vn.teca.hddt.dataapi.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.verify.CompareField;
import vn.teca.hddt.dataapi.domain.verify.ComparisonPolicy;

/** Bean nghiệp vụ dựng từ cấu hình + kiểm tra an toàn lúc khởi động. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(HddtProperties.class)
public class ApplicationConfig {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    public S3UrlPolicy s3UrlPolicy(HddtProperties props) {
        return new S3UrlPolicy(props.s3().allowedInputBuckets());
    }

    /** SI-13 (chốt 2026-10-31): so khớp tuyệt đối các cột dữ liệu HD1; rules-confirmed=false ⇒ API HD8 trả 501. */
    @Bean
    public ComparisonPolicy comparisonPolicy(HddtProperties props) {
        List<String> names = props.hd8().compareFields() == null ? Jdk8Compat.listOf() : props.hd8().compareFields();
        return new ComparisonPolicy(names.stream().map(CompareField::fromCsvName).collect(Jdk8Compat.toList()), props.hd8().moneyTolerance());
    }

    @Bean
    public StartupGuards startupGuards(HddtProperties props, Environment env) {
        return new StartupGuards(props, Arrays.asList(env.getActiveProfiles()));
    }

    /** Chặn cấu hình nguy hiểm ở môi trường prod. */
    public static final class StartupGuards {
        StartupGuards(HddtProperties props, List<String> profiles) {
            boolean prod = profiles.contains("prod");
            if (prod && props.tms().applyMode() == HddtProperties.Tms.ApplyMode.VALIDATE_ONLY) {
                throw new IllegalStateException("hddt.tms.apply-mode=VALIDATE_ONLY không được dùng ở prod");
            }
            if (props.security().clients() == null || props.security().clients().isEmpty()) {
                log.warn("Chưa cấu hình client nào: mọi request sẽ bị từ chối (401)");
            }
            if (props.hd8().rulesConfirmed() && (props.hd8().compareFields() == null || props.hd8().compareFields().isEmpty())) {
                throw new IllegalStateException("hddt.hd8.rules-confirmed=true nhưng chưa có compare-fields");
            }
            if (!props.mstStatus().configured()) {
                log.warn("Chưa cấu hình hddt.mst-status.source-sql: HD1/HD3/HD5/HD8 trả 503 (SI-24)");
            }
        }
    }
}
