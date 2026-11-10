package vn.teca.hddt.dataapi.application.csv;

import java.util.Objects;

/** Lỗi một dòng/cột của file đầu vào. Không chứa giá trị ô (tránh lộ dữ liệu NNT trong log/mta). */
public final class RowError {
    private final String objectKey;
    private final long recordNumber;
    private final String column;
    private final String reason;

    public RowError(String objectKey, long recordNumber, String column, String reason) {
        this.objectKey = objectKey;
        this.recordNumber = recordNumber;
        this.column = column;
        this.reason = reason;
    }

    public String objectKey() {
        return objectKey;
    }

    public long recordNumber() {
        return recordNumber;
    }

    public String column() {
        return column;
    }

    public String reason() {
        return reason;
    }

    public String describe() {
        return objectKey + " bản ghi " + recordNumber + (column == null ? "" : " cột " + column) + ": " + reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RowError)) {
            return false;
        }
        RowError other = (RowError) o;
        return Objects.equals(objectKey, other.objectKey)
                && recordNumber == other.recordNumber
                && Objects.equals(column, other.column)
                && Objects.equals(reason, other.reason);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(objectKey);
        result = 31 * result + Long.hashCode(recordNumber);
        result = 31 * result + Objects.hashCode(column);
        result = 31 * result + Objects.hashCode(reason);
        return result;
    }

    @Override
    public String toString() {
        return "RowError[objectKey=" + objectKey
                + ", recordNumber=" + recordNumber
                + ", column=" + column
                + ", reason=" + reason + "]";
    }
}
