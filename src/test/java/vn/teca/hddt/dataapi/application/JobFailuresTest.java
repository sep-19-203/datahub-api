package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import vn.teca.hddt.dataapi.application.job.JobFailures;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;

class JobFailuresTest {

    @Test
    void secretsInDorisErrorMessagesAreMasked() {
        String msg = "errCode = 2, detailMessage = INTO OUTFILE ... \"s3.access_key\" = \"AKIA123\", \"s3.secret_key\" = \"top/secret+key\"";
        String masked = JobFailures.mask(msg);
        assertThat(masked).doesNotContain("top/secret+key").doesNotContain("AKIA123").contains("\"***\"");
    }

    @Test
    void transientInfrastructureErrorsAreRetryable_businessErrorsAreNot() {
        assertThat(JobFailures.classify(new DataAccessResourceFailureException("doris down")).retryable()).isTrue();
        assertThat(JobFailures.classify(new RuntimeException(new SQLTransientConnectionException("x"))).retryable()).isTrue();
        assertThat(JobFailures.classify(new RuntimeException(new SocketTimeoutException())).retryable()).isTrue();
        assertThat(JobFailures.classify(new RuntimeException(new java.sql.SQLException("errCode = 2, detailMessage = "
                + "(10.0.0.9)[INTERNAL_ERROR]failed to put object: s3://b/k_0.csv:  curlCode: 28, Timeout was reached",
                "HY000"))).retryable()).isTrue();
        JobFailures.Failure f = JobFailures.classify(new PermanentJobException("HDDT-400-FILE", "File lỗi"));
        assertThat(f.retryable()).isFalse();
        assertThat(f.description()).isEqualTo("File lỗi");
        JobFailures.Failure unknown = JobFailures.classify(new IllegalStateException("NPE in SQL builder"));
        assertThat(unknown.retryable()).isFalse();
        assertThat(unknown.description()).doesNotContain("NPE");
    }
}
