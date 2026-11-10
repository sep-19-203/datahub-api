package vn.teca.hddt.dataapi.infrastructure.health;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * Health tách bạch: {@code jobStore} (DSE) quyết định readiness (không có kho job thì không nhận request); {@code doris}
 * chỉ báo trạng thái (Doris chậm không được làm pod bị gỡ khỏi Service — GET job vẫn phục vụ được).
 */
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator jobStore(CqlSession jobStoreSession) {
        return () -> {
            try {
                jobStoreSession.execute("SELECT release_version FROM system.local");
                return Health.up().build();
            } catch (RuntimeException e) {
                return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
            }
        };
    }

    @Bean
    public HealthIndicator doris(@Qualifier("dorisDataSource") DataSource dorisDataSource) {
        return () -> probe(dorisDataSource, 3);
    }

    private static Health probe(DataSource ds, int timeoutSeconds) {
        try {
            JdbcTemplate t = new JdbcTemplate(ds);
            t.setQueryTimeout(timeoutSeconds);
            t.queryForObject("SELECT 1", Integer.class);
            return Health.up().build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
