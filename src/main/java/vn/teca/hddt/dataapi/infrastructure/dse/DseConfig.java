package vn.teca.hddt.dataapi.infrastructure.dse;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Phiên DSE cho kho job. Mặc định LOCAL_QUORUM / LOCAL_SERIAL (LWT); KHÔNG đặt idempotent mặc định để driver không tự
 * gửi lại LWT khi timeout (kết quả LWT không xác định được xử lý ở repository); timestamp ghi phía server.
 */
@Configuration
public class DseConfig {
    private static final Logger log = LoggerFactory.getLogger(DseConfig.class);
    private static final Pattern IDENT = Pattern.compile("[a-z][a-z0-9_]{0,47}");

    @Bean(destroyMethod = "close")
    public CqlSession jobStoreSession(HddtProperties props) {
        HddtProperties.Dse d = props.dse();
        if (!IDENT.matcher(d.keyspace()).matches()) {
            throw new IllegalStateException("hddt.dse.keyspace không hợp lệ");
        }
        List<InetSocketAddress> points = new ArrayList<>();
        for (String hp : d.contactPoints()) {
            for (String one : hp.split(",")) {
                String[] parts = one.trim().split(":");
                if (!Jdk8Compat.isBlank(parts[0])) {
                    points.add(new InetSocketAddress(parts[0].trim(), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 9042));
                }
            }
        }
        DriverConfigLoader cfg = DriverConfigLoader.programmaticBuilder()
                .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
                .withString(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY, "LOCAL_SERIAL")
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, d.requestTimeout())
                .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, d.connectTimeout())
                .withDuration(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, d.connectTimeout())
                .withDuration(DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, d.requestTimeout())
                .withBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE, false)
                // Timestamp ghi lấy phía server: trộn ghi thường (timestamp client) với LWT (timestamp Paxos phía server)
                // khi đồng hồ client chạy nhanh hơn server làm cập nhật LWT bị "đè" (đã tái hiện trên DSE DEV 31/10).
                .withString(DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, "ServerSideTimestampGenerator")
                .build();
        CqlSessionBuilder b = CqlSession.builder().addContactPoints(points).withLocalDatacenter(d.localDatacenter())
                .withConfigLoader(cfg);
        if (d.username() != null && !Jdk8Compat.isBlank(d.username())) {
            b = b.withAuthCredentials(d.username(), d.password() == null ? "" : d.password());
        }
        CqlSession session = b.build();
        if (d.createSchema()) {
            installSchema(session, d);
        }
        return session;
    }

    static final List<String> SCRIPTS = Jdk8Compat.listOf("V1__job_store.cql", "V2__refund_ledger_staging.cql");

    /** Chạy lần lượt các script cql/V*.cql — idempotent (IF NOT EXISTS; ALTER đã áp dụng thì bỏ qua). */
    public static void installSchema(CqlSession session, HddtProperties.Dse d) {
        for (String name : SCRIPTS) {
            runScript(session, d, name);
        }
        log.info("Đã kiểm tra/tạo schema kho job DSE keyspace={}", d.keyspace());
    }

    private static void runScript(CqlSession session, HddtProperties.Dse d, String name) {
        String script;
        try (InputStream in = DseConfig.class.getResourceAsStream("/cql/" + name)) {
            script = new String(Jdk8Compat.readAllBytes(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được cql/" + name, e);
        }
        StringBuilder noComments = new StringBuilder();
        for (String line : script.split("\n")) {
            if (!line.trim().startsWith("--")) {
                noComments.append(line).append('\n');
            }
        }
        String resolved = noComments.toString().replace("${KS}", d.keyspace()).replace("${REPLICATION}", d.replication())
                .replace("${JOB_TTL}", String.valueOf(d.jobTtlSeconds()));
        for (String stmt : resolved.split(";")) {
            if (Jdk8Compat.isBlank(stmt)) {
                continue;
            }
            try {
                session.execute(stmt.trim());
            } catch (com.datastax.oss.driver.api.core.servererrors.InvalidQueryException e) {
                String m = String.valueOf(e.getMessage()).toLowerCase();
                if (!(m.contains("already exist") || m.contains("conflicts with"))) {
                    throw e;
                }
            }
        }
    }
}
