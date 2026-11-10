package vn.teca.hddt.dataapi.application.job;

/** Lỗi không thử lại (dữ liệu đầu vào sai, nghiệp vụ bị chặn...). {@code description} an toàn để trả trong `mta`. */
public class PermanentJobException extends RuntimeException {
    private final String errorCode;
    private final String description;

    public PermanentJobException(String errorCode, String description) {
        this(errorCode, description, null);
    }

    public PermanentJobException(String errorCode, String description, Throwable cause) {
        super(description, cause);
        this.errorCode = errorCode;
        this.description = description;
    }

    public String errorCode() {
        return errorCode;
    }

    public String description() {
        return description;
    }
}
