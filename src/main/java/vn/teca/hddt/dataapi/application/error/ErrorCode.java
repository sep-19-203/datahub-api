package vn.teca.hddt.dataapi.application.error;

/**
 * Danh mục mã lỗi (đề xuất SI-37 — đặc tả chỉ có errorCode "0"). {@code code} = trường `code`, {@code errorCode} = trường
 * `errorCode`, {@code httpStatus} = trường `status` và mã HTTP.
 */
public enum ErrorCode {
    OK(200, "OK", "0", "Thành công"),
    ACCEPTED(202, "OK", "0", "Thành công"),
    BAD_REQUEST(400, "BAD_REQUEST", "HDDT-400", "Dữ liệu yêu cầu không hợp lệ"),
    INVALID_HEADER(400, "BAD_REQUEST", "HDDT-400-HEADER", "Thiếu hoặc sai header bắt buộc"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "HDDT-401", "Xác thực client không hợp lệ"),
    FORBIDDEN(403, "FORBIDDEN", "HDDT-403", "Client không có quyền thực hiện yêu cầu này"),
    INVOICE_NOT_FOUND(404, "NOT_FOUND", "HDDT-404", "Không tìm thấy hóa đơn"),
    JOB_NOT_FOUND(404, "NOT_FOUND", "HDDT-404-JOB", "Không tìm thấy job"),
    METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED", "HDDT-405", "Phương thức không được hỗ trợ"),
    AMBIGUOUS_INVOICE(409, "CONFLICT", "HDDT-409-AMBIGUOUS", "Khóa tra cứu trả về nhiều hóa đơn"),
    IDEMPOTENCY_CONFLICT(409, "CONFLICT", "HDDT-409-IDEMPOTENCY", "X-Request-Id đã được dùng với nội dung khác"),
    PAYLOAD_TOO_LARGE(413, "PAYLOAD_TOO_LARGE", "HDDT-413", "Yêu cầu vượt kích thước cho phép"),
    UNSUPPORTED_MEDIA_TYPE(415, "UNSUPPORTED_MEDIA_TYPE", "HDDT-415", "Content-Type phải là application/json"),
    TOO_MANY_REQUESTS(429, "TOO_MANY_REQUESTS", "HDDT-429", "Vượt giới hạn yêu cầu hoặc số job đồng thời"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "HDDT-500", "Lỗi hệ thống"),
    MAPPING_NOT_CONFIRMED(501, "NOT_IMPLEMENTED", "HDDT-501-MAPPING", "Chỉ tiêu chưa có ánh xạ nguồn được phê duyệt"),
    RULE_NOT_CONFIRMED(501, "NOT_IMPLEMENTED", "HDDT-501-RULE", "Quy tắc nghiệp vụ chưa được phê duyệt"),
    MST_STATUS_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "HDDT-503-MST-STATUS", "Nguồn trạng thái MST chưa sẵn sàng"),
    DATA_SOURCE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "HDDT-503-DORIS", "Kho dữ liệu tạm thời không khả dụng"),
    QUERY_TIMEOUT(504, "GATEWAY_TIMEOUT", "HDDT-504", "Truy vấn vượt thời gian cho phép");

    private final int httpStatus;
    private final String code;
    private final String errorCode;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String errorCode, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.errorCode = errorCode;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String errorCode() {
        return errorCode;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
