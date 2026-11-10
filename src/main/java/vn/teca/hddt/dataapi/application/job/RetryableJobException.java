package vn.teca.hddt.dataapi.application.job;

/** Lỗi tạm thời (mất kết nối Doris/S3, kết quả xuất chưa nhất quán...) ⇒ thử lại có backoff. */
public class RetryableJobException extends RuntimeException {
    public RetryableJobException(String message) {
        super(message);
    }

    public RetryableJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
