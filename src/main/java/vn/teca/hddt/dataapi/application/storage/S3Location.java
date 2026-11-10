package vn.teca.hddt.dataapi.application.storage;

import java.util.Objects;

/** Vị trí S3 đã kiểm tra: khóa cụ thể hoặc tiền tố (URL kết thúc '*'). */
public final class S3Location {
    private final String bucket;
    private final String key;
    private final boolean prefix;

    public S3Location(String bucket, String key, boolean prefix) {
        this.bucket = bucket;
        this.key = key;
        this.prefix = prefix;
    }

    public String bucket() {
        return bucket;
    }

    public String key() {
        return key;
    }

    public boolean prefix() {
        return prefix;
    }

    public String toUri() {
        return "s3://" + bucket + "/" + key + (prefix ? "*" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof S3Location)) {
            return false;
        }
        S3Location other = (S3Location) o;
        return Objects.equals(bucket, other.bucket)
                && Objects.equals(key, other.key)
                && prefix == other.prefix;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(bucket);
        result = 31 * result + Objects.hashCode(key);
        result = 31 * result + Boolean.hashCode(prefix);
        return result;
    }

    @Override
    public String toString() {
        return "S3Location[bucket=" + bucket
                + ", key=" + key
                + ", prefix=" + prefix + "]";
    }
}
