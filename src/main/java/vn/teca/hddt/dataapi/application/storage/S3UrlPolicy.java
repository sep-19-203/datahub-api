package vn.teca.hddt.dataapi.application.storage;

import java.util.List;
import java.util.regex.Pattern;

import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Kiểm tra `s3url` do client gửi (chống SSRF/path abuse — SI-07): chỉ scheme s3://, bucket trong whitelist, khóa nằm
 * dưới tiền tố được phép cho từng API, không "..", không "//", không ký tự điều khiển, '*' chỉ ở cuối.
 * Không bao giờ tải URL HTTP tùy ý.
 */
public final class S3UrlPolicy {
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final Pattern KEY_CHARS = Pattern.compile("[A-Za-z0-9!_.\\-*/()=+,@ ]+");

    private final List<String> allowedBuckets;

    public S3UrlPolicy(List<String> allowedBuckets) {
        this.allowedBuckets = Jdk8Compat.listCopyOf(allowedBuckets);
    }

    public S3Location parse(String url, List<String> allowedPrefixes) {
        if (url == null || url.length() > 1024 || !url.startsWith("s3://")) {
            throw invalid("s3url phải có dạng s3://bucket/khoa");
        }
        String rest = url.substring(5);
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw invalid("s3url thiếu bucket hoặc khóa");
        }
        String bucket = rest.substring(0, slash);
        String key = rest.substring(slash + 1);
        if (!BUCKET.matcher(bucket).matches() || !allowedBuckets.contains(bucket)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bucket không nằm trong danh sách được phép");
        }
        if (!KEY_CHARS.matcher(key).matches() || key.contains("..") || key.contains("//") || key.startsWith("/")) {
            throw invalid("Khóa S3 chứa ký tự hoặc đoạn đường dẫn không hợp lệ");
        }
        boolean prefix = key.endsWith("*");
        String k = prefix ? key.substring(0, key.length() - 1) : key;
        if (k.contains("*")) {
            throw invalid("Ký tự '*' chỉ được dùng ở cuối s3url");
        }
        if (k.isEmpty() || allowedPrefixes.stream().noneMatch(k::startsWith)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Khóa S3 không thuộc thư mục được phép cho API này");
        }
        return new S3Location(bucket, k, prefix);
    }

    private static ApiException invalid(String msg) {
        return new ApiException(ErrorCode.BAD_REQUEST, msg);
    }
}
