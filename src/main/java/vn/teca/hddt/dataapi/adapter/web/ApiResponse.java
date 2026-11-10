package vn.teca.hddt.dataapi.adapter.web;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import vn.teca.hddt.dataapi.application.error.ErrorCode;

/** Envelope chung (HD1!C24–C29…): traceId (không phải "tracedId" — SI-01), status, code, errorCode, message, data. */
@JsonPropertyOrder({"traceId", "status", "code", "errorCode", "message", "data"})
public final class ApiResponse<T> {
    @JsonProperty("traceId")
    private final String traceId;
    @JsonProperty("status")
    private final int status;
    @JsonProperty("code")
    private final String code;
    @JsonProperty("errorCode")
    private final String errorCode;
    @JsonProperty("message")
    private final String message;
    @JsonProperty("data")
    private final T data;

    @JsonCreator
    public ApiResponse(@JsonProperty("traceId") String traceId, @JsonProperty("status") int status,
            @JsonProperty("code") String code, @JsonProperty("errorCode") String errorCode,
            @JsonProperty("message") String message, @JsonProperty("data") T data) {
        this.traceId = traceId;
        this.status = status;
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
    }

    public String traceId() {
        return traceId;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String errorCode() {
        return errorCode;
    }

    public String message() {
        return message;
    }

    public T data() {
        return data;
    }

    public static <T> ApiResponse<T> ok(String traceId, int httpStatus, T data) {
        return new ApiResponse<>(traceId, httpStatus, ErrorCode.OK.code(), ErrorCode.OK.errorCode(),
                ErrorCode.OK.defaultMessage(), data);
    }

    public static ApiResponse<Void> error(String traceId, ErrorCode code, String message) {
        return new ApiResponse<>(traceId, code.httpStatus(), code.code(), code.errorCode(),
                message == null ? code.defaultMessage() : message, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiResponse)) {
            return false;
        }
        ApiResponse<?> other = (ApiResponse<?>) o;
        return Objects.equals(traceId, other.traceId)
                && status == other.status
                && Objects.equals(code, other.code)
                && Objects.equals(errorCode, other.errorCode)
                && Objects.equals(message, other.message)
                && Objects.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(traceId);
        result = 31 * result + Integer.hashCode(status);
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(errorCode);
        result = 31 * result + Objects.hashCode(message);
        result = 31 * result + Objects.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "ApiResponse[traceId=" + traceId
                + ", status=" + status
                + ", code=" + code
                + ", errorCode=" + errorCode
                + ", message=" + message
                + ", data=" + data + "]";
    }
}
