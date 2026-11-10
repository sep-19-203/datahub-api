package vn.teca.hddt.dataapi.application.port;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.invoice.Direction;

/** Kết xuất danh sách HD1/HD5 ra S3 (Doris OUTFILE hoặc stream JDBC — cùng biểu thức dòng CSV sinh trong SQL). */
public interface ExportPort {

    static final class ExportSpec {
        private final UUID jobId;
        private final int attemptNo;
        private final Direction direction;
        private final String mst;
        private final MonthRange range;
        private final boolean excludeMstStatus;
        private final List<String> excludedStatusCodes;
        private final boolean excludeIllegal;
        private final String bucket;
        private final String prefix;

        public ExportSpec(UUID jobId, int attemptNo, Direction direction, String mst, MonthRange range,
                boolean excludeMstStatus, List<String> excludedStatusCodes, boolean excludeIllegal, String bucket,
                String prefix) {
            this.jobId = jobId;
            this.attemptNo = attemptNo;
            this.direction = direction;
            this.mst = mst;
            this.range = range;
            this.excludeMstStatus = excludeMstStatus;
            this.excludedStatusCodes = excludedStatusCodes;
            this.excludeIllegal = excludeIllegal;
            this.bucket = bucket;
            this.prefix = prefix;
        }

        public UUID jobId() {
            return jobId;
        }

        public int attemptNo() {
            return attemptNo;
        }

        public Direction direction() {
            return direction;
        }

        public String mst() {
            return mst;
        }

        public MonthRange range() {
            return range;
        }

        public boolean excludeMstStatus() {
            return excludeMstStatus;
        }

        public List<String> excludedStatusCodes() {
            return excludedStatusCodes;
        }

        public boolean excludeIllegal() {
            return excludeIllegal;
        }

        public String bucket() {
            return bucket;
        }

        public String prefix() {
            return prefix;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ExportSpec)) {
                return false;
            }
            ExportSpec other = (ExportSpec) o;
            return Objects.equals(jobId, other.jobId)
                    && attemptNo == other.attemptNo
                    && Objects.equals(direction, other.direction)
                    && Objects.equals(mst, other.mst)
                    && Objects.equals(range, other.range)
                    && excludeMstStatus == other.excludeMstStatus
                    && Objects.equals(excludedStatusCodes, other.excludedStatusCodes)
                    && excludeIllegal == other.excludeIllegal
                    && Objects.equals(bucket, other.bucket)
                    && Objects.equals(prefix, other.prefix);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(jobId);
            result = 31 * result + Integer.hashCode(attemptNo);
            result = 31 * result + Objects.hashCode(direction);
            result = 31 * result + Objects.hashCode(mst);
            result = 31 * result + Objects.hashCode(range);
            result = 31 * result + Boolean.hashCode(excludeMstStatus);
            result = 31 * result + Objects.hashCode(excludedStatusCodes);
            result = 31 * result + Boolean.hashCode(excludeIllegal);
            result = 31 * result + Objects.hashCode(bucket);
            result = 31 * result + Objects.hashCode(prefix);
            return result;
        }

        @Override
        public String toString() {
            return "ExportSpec[jobId=" + jobId
                    + ", attemptNo=" + attemptNo
                    + ", direction=" + direction
                    + ", mst=" + mst
                    + ", range=" + range
                    + ", excludeMstStatus=" + excludeMstStatus
                    + ", excludedStatusCodes=" + excludedStatusCodes
                    + ", excludeIllegal=" + excludeIllegal
                    + ", bucket=" + bucket
                    + ", prefix=" + prefix + "]";
        }
    }

    /** Kết quả do engine báo: số file, số dòng, tổng byte, URL pattern dạng s3://bucket/prefix&lt;id&gt;_*. */
    static final class ExportReport {
        private final long fileCount;
        private final long rowCount;
        private final long totalBytes;
        private final String url;
        private final String dataKeyPrefix;

        public ExportReport(long fileCount, long rowCount, long totalBytes, String url, String dataKeyPrefix) {
            this.fileCount = fileCount;
            this.rowCount = rowCount;
            this.totalBytes = totalBytes;
            this.url = url;
            this.dataKeyPrefix = dataKeyPrefix;
        }

        public long fileCount() {
            return fileCount;
        }

        public long rowCount() {
            return rowCount;
        }

        public long totalBytes() {
            return totalBytes;
        }

        public String url() {
            return url;
        }

        public String dataKeyPrefix() {
            return dataKeyPrefix;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ExportReport)) {
                return false;
            }
            ExportReport other = (ExportReport) o;
            return fileCount == other.fileCount
                    && rowCount == other.rowCount
                    && totalBytes == other.totalBytes
                    && Objects.equals(url, other.url)
                    && Objects.equals(dataKeyPrefix, other.dataKeyPrefix);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(fileCount);
            result = 31 * result + Long.hashCode(rowCount);
            result = 31 * result + Long.hashCode(totalBytes);
            result = 31 * result + Objects.hashCode(url);
            result = 31 * result + Objects.hashCode(dataKeyPrefix);
            return result;
        }

        @Override
        public String toString() {
            return "ExportReport[fileCount=" + fileCount
                    + ", rowCount=" + rowCount
                    + ", totalBytes=" + totalBytes
                    + ", url=" + url
                    + ", dataKeyPrefix=" + dataKeyPrefix + "]";
        }
    }

    ExportReport export(ExportSpec spec);

    /**
     * Số dòng sẽ xuất, tối đa {@code limit} (cùng điều kiện lọc với {@link #export}) — chỉ dùng tham số lọc của spec.
     * Engine không hỗ trợ đếm ⇒ ném lỗi, bên gọi bỏ qua chế độ trả ngay (SI-06).
     */
    default long countUpTo(ExportSpec spec, long limit) {
        throw new UnsupportedOperationException("Engine kết xuất không hỗ trợ đếm trước");
    }
}
