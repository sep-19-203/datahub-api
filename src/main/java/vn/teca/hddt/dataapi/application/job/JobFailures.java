package vn.teca.hddt.dataapi.application.job;

import java.io.IOException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Phân loại lỗi job và làm sạch thông điệp (che secret S3 có thể bị Doris lặp lại trong thông báo lỗi). */
public final class JobFailures {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\"?s3\\.(secret_key|access_key|session_token)\"?\\s*=\\s*)\"[^\"]*\"");

    public static final class Failure {
        private final boolean retryable;
        private final String errorCode;
        private final String description;
        private final String internalMessage;

        public Failure(boolean retryable, String errorCode, String description, String internalMessage) {
            this.retryable = retryable;
            this.errorCode = errorCode;
            this.description = description;
            this.internalMessage = internalMessage;
        }

        public boolean retryable() {
            return retryable;
        }

        public String errorCode() {
            return errorCode;
        }

        public String description() {
            return description;
        }

        public String internalMessage() {
            return internalMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Failure)) {
                return false;
            }
            Failure other = (Failure) o;
            return retryable == other.retryable
                    && Objects.equals(errorCode, other.errorCode)
                    && Objects.equals(description, other.description)
                    && Objects.equals(internalMessage, other.internalMessage);
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(retryable);
            result = 31 * result + Objects.hashCode(errorCode);
            result = 31 * result + Objects.hashCode(description);
            result = 31 * result + Objects.hashCode(internalMessage);
            return result;
        }

        @Override
        public String toString() {
            return "Failure[retryable=" + retryable
                    + ", errorCode=" + errorCode
                    + ", description=" + description
                    + ", internalMessage=" + internalMessage + "]";
        }
    }

    private JobFailures() {
    }

    public static Failure classify(Throwable t) {
        if (t instanceof PermanentJobException) {
            PermanentJobException p = (PermanentJobException) t;
            return new Failure(false, p.errorCode(), p.description(), mask(String.valueOf(rootMessage(t))));
        }
        if (isRetryable(t)) {
            return new Failure(true, "HDDT-503", "Lỗi tạm thời khi xử lý, hệ thống sẽ thử lại",
                    mask(t.getClass().getSimpleName() + ": " + rootMessage(t)));
        }
        return new Failure(false, "HDDT-500", "Lỗi hệ thống khi xử lý job",
                mask(t.getClass().getSimpleName() + ": " + rootMessage(t)));
    }

    static boolean isRetryable(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof RetryableJobException || c instanceof TransientDataAccessException
                    || c instanceof RecoverableDataAccessException || c instanceof DataAccessResourceFailureException
                    || c instanceof SQLTransientException || c instanceof SQLRecoverableException
                    || c instanceof IOException) {
                return true;
            }
            if (c instanceof S3Exception) {
                S3Exception s3 = (S3Exception) c;
                return s3.statusCode() >= 500 || s3.statusCode() == 429;
            }
            if (c instanceof SdkClientException) {
                return true;
            }
            if (c instanceof java.sql.SQLException) {
                java.sql.SQLException sql = (java.sql.SQLException) c;
                if (sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                    return true;
                }
                // Doris BE ghi S3 lỗi mạng (OUTFILE, kiểm chứng DEV 4.0.8: "failed to put object ... curlCode: 28")
                String m = sql.getMessage();
                if (m != null && (m.contains("failed to put object") || m.contains("curlCode")
                        || m.contains("Timeout was reached"))) {
                    return true;
                }
            }
            if (c == c.getCause()) {
                break;
            }
        }
        return false;
    }

    static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage();
    }

    public static String mask(String s) {
        if (s == null) {
            return null;
        }
        String masked = SECRET.matcher(s).replaceAll("$1\"***\"");
        return masked.length() > 1900 ? masked.substring(0, 1900) : masked;
    }
}
