package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.storage.S3Location;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

class S3UrlPolicyTest {
    final S3UrlPolicy policy = new S3UrlPolicy(Jdk8Compat.listOf("hddt"));
    final List<String> hd8 = Jdk8Compat.listOf("import/tthc/", "export/query-result/");

    @Test
    void acceptsSpecExamplePattern_asPrefix() {
        S3Location l = policy.parse("s3://hddt/export/query-result/26cbb6dc-e896-45d9-bbc6-cc1d640d091f/"
                + "c852ec478bc8afffb49e7-a82661eec9d995ad_*", hd8);
        assertThat(l.prefix()).isTrue();
        assertThat(l.bucket()).isEqualTo("hddt");
        assertThat(l.key()).endsWith("_");
    }

    @Test
    void acceptsExactKey() {
        assertThat(policy.parse("s3://hddt/import/tthc/a.csv", hd8).prefix()).isFalse();
    }

    @Test
    void rejectsHttpUrls_otherBuckets_traversal_andForeignPrefixes() {
        assertCode("http://169.254.169.254/latest/meta-data", ErrorCode.BAD_REQUEST);
        assertCode("https://hddt.s3/import/tthc/a.csv", ErrorCode.BAD_REQUEST);
        assertCode("s3://other-bucket/import/tthc/a.csv", ErrorCode.FORBIDDEN);
        assertCode("s3://hddt/import/tthc/../../internal/jobs/x", ErrorCode.BAD_REQUEST);
        assertCode("s3://hddt//import/tthc/a.csv", ErrorCode.BAD_REQUEST);
        assertCode("s3://hddt/internal/jobs/secret.csv", ErrorCode.FORBIDDEN);
        assertCode("s3://hddt/import/tthc/*/a.csv", ErrorCode.BAD_REQUEST);
        assertCode("s3://hddt/import/tthc/a\nb.csv", ErrorCode.BAD_REQUEST);
        assertCode("s3://hddt/", ErrorCode.BAD_REQUEST);
    }

    @Test
    void tmsCannotReadTthcFolder() {
        assertThatThrownBy(() -> policy.parse("s3://hddt/import/tthc/a.csv", Jdk8Compat.listOf("import/tms/")))
                .isInstanceOf(ApiException.class);
    }

    private void assertCode(String url, ErrorCode code) {
        assertThatThrownBy(() -> policy.parse(url, hd8)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode()).isEqualTo(code));
    }
}
