package vn.teca.hddt.dataapi.domain.common;

/** Dữ liệu nghiệp vụ không hợp lệ (định dạng, khoảng tháng...). Thông điệp an toàn để trả cho client. */
public class DomainValidationException extends RuntimeException {
    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
