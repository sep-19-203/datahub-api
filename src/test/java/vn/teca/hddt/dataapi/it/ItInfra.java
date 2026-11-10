package vn.teca.hddt.dataapi.it;

import java.net.URI;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Hạ tầng cho test tích hợp. Ưu tiên biến môi trường (IT_DSE_CONTACT_POINTS, IT_S3_ENDPOINT, IT_DORIS_URL — credential qua
 * IT_*_USER/PASSWORD/KEY, không ghi vào repo); nếu không có thì Testcontainers (cần Docker); không có cả hai ⇒ bỏ qua.
 */
public final class ItInfra {
    private static MinIOContainer minio;

    private ItInfra() {
    }

    static String env(String k) {
        String v = System.getenv(k);
        return v == null || Jdk8Compat.isBlank(v) ? null : v;
    }

    static boolean docker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** DSE (kho job) chỉ qua biến môi trường IT_DSE_CONTACT_POINTS, IT_DSE_LOCAL_DC, IT_DSE_USERNAME, IT_DSE_PASSWORD. */
    public static CqlSession dse() {
        String points = env("IT_DSE_CONTACT_POINTS");
        Assumptions.assumeTrue(points != null, "Không có IT_DSE_CONTACT_POINTS ⇒ bỏ qua IT DSE");
        CqlSessionBuilder b = CqlSession.builder().withLocalDatacenter(env("IT_DSE_LOCAL_DC") == null ? "DC1" : env("IT_DSE_LOCAL_DC"));
        for (String hp : points.split(",")) {
            String[] parts = hp.trim().split(":");
            b = b.addContactPoint(new java.net.InetSocketAddress(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 9042));
        }
        if (env("IT_DSE_USERNAME") != null) {
            b = b.withAuthCredentials(env("IT_DSE_USERNAME"), env("IT_DSE_PASSWORD") == null ? "" : env("IT_DSE_PASSWORD"));
        }
        return b.withConfigLoader(com.datastax.oss.driver.api.core.config.DriverConfigLoader.programmaticBuilder()
                .withString(com.datastax.oss.driver.api.core.config.DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
                .withString(com.datastax.oss.driver.api.core.config.DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY, "LOCAL_SERIAL")
                .withString(com.datastax.oss.driver.api.core.config.DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, "ServerSideTimestampGenerator")
                .withDuration(com.datastax.oss.driver.api.core.config.DefaultDriverOption.REQUEST_TIMEOUT, java.time.Duration.ofSeconds(15))
                .withDuration(com.datastax.oss.driver.api.core.config.DefaultDriverOption.METADATA_SCHEMA_REQUEST_TIMEOUT, java.time.Duration.ofSeconds(30))
                .build()).build();
    }

    public static synchronized S3Client s3() {
        String endpoint = env("IT_S3_ENDPOINT");
        String ak = env("IT_S3_ACCESS_KEY");
        String sk = env("IT_S3_SECRET_KEY");
        if (endpoint == null) {
            Assumptions.assumeTrue(docker(), "Không có IT_S3_ENDPOINT và Docker ⇒ bỏ qua IT S3");
            if (minio == null) {
                minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");
                minio.start();
            }
            endpoint = minio.getS3URL();
            ak = minio.getUserName();
            sk = minio.getPassword();
        }
        return S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ak, sk)))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    /** Doris chỉ qua biến môi trường (không chạy Doris trong Testcontainers). */
    public static HikariDataSource doris() {
        String url = env("IT_DORIS_URL");
        Assumptions.assumeTrue(url != null, "Không có IT_DORIS_URL ⇒ bỏ qua IT Doris");
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(env("IT_DORIS_USER") == null ? "root" : env("IT_DORIS_USER"));
        ds.setPassword(env("IT_DORIS_PASSWORD") == null ? "" : env("IT_DORIS_PASSWORD"));
        ds.setMaximumPoolSize(4);
        return ds;
    }
}
