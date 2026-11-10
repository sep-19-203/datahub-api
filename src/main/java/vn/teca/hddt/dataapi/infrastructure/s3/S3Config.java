package vn.teca.hddt.dataapi.infrastructure.s3;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** S3Client cho MinIO/S3 nội bộ: path-style, checksum chỉ khi bắt buộc (tương thích MinIO). Credential từ env/Secret. */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(HddtProperties props) {
        HddtProperties.S3 c = props.s3();
        AwsCredentialsProvider creds = c.accessKey() == null || Jdk8Compat.isBlank(c.accessKey())
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(c.accessKey(), c.secretKey()));
        S3ClientBuilder b = S3Client.builder()
                .region(Region.of(c.region()))
                .credentialsProvider(creds)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(c.pathStyle()).build());
        if (c.endpoint() != null && !Jdk8Compat.isBlank(c.endpoint())) {
            b.endpointOverride(URI.create(c.endpoint()));
        }
        return b.build();
    }
}
