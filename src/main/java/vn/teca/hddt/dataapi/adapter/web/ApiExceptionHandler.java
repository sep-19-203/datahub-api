package vn.teca.hddt.dataapi.adapter.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import vn.teca.hddt.dataapi.adapter.web.security.BodyLimitRequestWrapper;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.job.JobFailures;
import vn.teca.hddt.dataapi.domain.common.BusinessRuleNotConfirmedException;
import vn.teca.hddt.dataapi.domain.common.DomainValidationException;

/** Ánh xạ lỗi → envelope chuẩn. Lỗi 5xx không lộ chi tiết nội bộ cho client. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ResponseEntity<ApiResponse<Void>> respond(HttpServletRequest req, ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus()).body(ApiResponse.error(RequestContext.traceId(req), code, message));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException e, HttpServletRequest req) {
        return respond(req, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ApiResponse<Void>> domain(DomainValidationException e, HttpServletRequest req) {
        return respond(req, ErrorCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleNotConfirmedException.class)
    ResponseEntity<ApiResponse<Void>> blocked(BusinessRuleNotConfirmedException e, HttpServletRequest req) {
        ErrorCode code = e.kind() == BusinessRuleNotConfirmedException.Kind.MAPPING
                ? ErrorCode.MAPPING_NOT_CONFIRMED : ErrorCode.RULE_NOT_CONFIRMED;
        return respond(req, code, e.getMessage() + " (" + e.issueRef() + ")");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().collect(Collectors.joining("; "));
        return respond(req, ErrorCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof BodyLimitRequestWrapper.PayloadTooLargeException) {
                return respond(req, ErrorCode.PAYLOAD_TOO_LARGE, null);
            }
        }
        return respond(req, ErrorCode.BAD_REQUEST, "Body JSON không hợp lệ hoặc có trường không thuộc hợp đồng");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> missingParam(MissingServletRequestParameterException e, HttpServletRequest req) {
        return respond(req, ErrorCode.BAD_REQUEST, "Thiếu tham số " + e.getParameterName());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return respond(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return respond(req, ErrorCode.METHOD_NOT_ALLOWED, null);
    }

    // Spring 5.3: cần spring.mvc.throw-exception-if-no-handler-found=true + spring.web.resources.add-mappings=false
    // (application.yml) — tương đương NoResourceFoundException của Spring 6.1 ở bản trước migration.
    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(NoHandlerFoundException e, HttpServletRequest req) {
        return respond(req, ErrorCode.INVOICE_NOT_FOUND, "Không có API tại đường dẫn này");
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ResponseEntity<ApiResponse<Void>> timeout(QueryTimeoutException e, HttpServletRequest req) {
        log.warn("Truy vấn quá thời gian: {}", JobFailures.mask(e.getMessage()));
        return respond(req, ErrorCode.QUERY_TIMEOUT, null);
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ApiResponse<Void>> unavailable(DataAccessException e, HttpServletRequest req) {
        log.error("Kho dữ liệu không khả dụng: {}", JobFailures.mask(e.getMessage()));
        return respond(req, ErrorCode.DATA_SOURCE_UNAVAILABLE, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> other(Exception e, HttpServletRequest req) {
        log.error("Lỗi không xử lý được", e);
        return respond(req, ErrorCode.INTERNAL_ERROR, null);
    }
}
