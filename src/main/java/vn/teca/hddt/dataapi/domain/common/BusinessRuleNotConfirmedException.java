package vn.teca.hddt.dataapi.domain.common;

/**
 * Chức năng bị chặn vì ánh xạ nguồn / quy tắc nghiệp vụ chưa được phê duyệt (trạng thái BLOCKED trong API_SPEC_ISSUES.md).
 * Không bao giờ thay bằng số 0 hay giá trị giả.
 */
public class BusinessRuleNotConfirmedException extends RuntimeException {
    public enum Kind { MAPPING, RULE }

    private final Kind kind;
    private final String issueRef;

    public BusinessRuleNotConfirmedException(Kind kind, String issueRef, String message) {
        super(message);
        this.kind = kind;
        this.issueRef = issueRef;
    }

    public Kind kind() {
        return kind;
    }

    public String issueRef() {
        return issueRef;
    }
}
