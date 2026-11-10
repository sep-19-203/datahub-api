package vn.teca.hddt.dataapi.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Nguồn JDBC duy nhất là Doris (chỉ đọc + OUTFILE); metadata job ở DSE ({@code DseConfig}). Ba JdbcTemplate với timeout
 * khác nhau: đồng bộ (HD3/HD7), tra lô (HD8/TMS), kết xuất.
 */
@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    @Primary
    public HikariDataSource dorisDataSource(HddtProperties props) {
        HddtProperties.Doris d = props.doris();
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("doris");
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl(d.jdbcUrl());
        ds.setUsername(d.username());
        ds.setPassword(d.password());
        ds.setMaximumPoolSize(d.poolSize());
        ds.setMinimumIdle(Math.min(2, d.poolSize()));
        ds.setConnectionTimeout(10_000);
        ds.setValidationTimeout(5_000);
        ds.setReadOnly(false); // OUTFILE cần phiên thường; quyền ghi bị chặn ở tầng tài khoản Doris (chỉ SELECT + OUTFILE)
        ds.setInitializationFailTimeout(-1); // không chặn khởi động khi Doris tạm thời không sẵn sàng
        return ds;
    }

    @Bean
    public JdbcTemplate dorisSyncJdbc(@Qualifier("dorisDataSource") DataSource ds, HddtProperties props) {
        return template(ds, props.doris().syncQueryTimeoutSeconds());
    }

    @Bean
    public JdbcTemplate dorisLookupJdbc(@Qualifier("dorisDataSource") DataSource ds, HddtProperties props) {
        return template(ds, props.doris().lookupQueryTimeoutSeconds());
    }

    @Bean
    public JdbcTemplate dorisExportJdbc(@Qualifier("dorisDataSource") DataSource ds, HddtProperties props) {
        return template(ds, props.doris().exportQueryTimeoutSeconds());
    }

    private static JdbcTemplate template(DataSource ds, int timeoutSeconds) {
        JdbcTemplate t = new JdbcTemplate(ds);
        t.setQueryTimeout(timeoutSeconds);
        return t;
    }
}
