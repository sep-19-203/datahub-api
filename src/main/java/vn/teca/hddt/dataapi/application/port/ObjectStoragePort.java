package vn.teca.hddt.dataapi.application.port;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/** Lưu trữ đối tượng S3/MinIO. */
public interface ObjectStoragePort {

    static final class StoredObject {
        private final String bucket;
        private final String key;
        private final long size;
        private final String etag;

        public StoredObject(String bucket, String key, long size, String etag) {
            this.bucket = bucket;
            this.key = key;
            this.size = size;
            this.etag = etag;
        }

        public String bucket() {
            return bucket;
        }

        public String key() {
            return key;
        }

        public long size() {
            return size;
        }

        public String etag() {
            return etag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StoredObject)) {
                return false;
            }
            StoredObject other = (StoredObject) o;
            return Objects.equals(bucket, other.bucket)
                    && Objects.equals(key, other.key)
                    && size == other.size
                    && Objects.equals(etag, other.etag);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(bucket);
            result = 31 * result + Objects.hashCode(key);
            result = 31 * result + Long.hashCode(size);
            result = 31 * result + Objects.hashCode(etag);
            return result;
        }

        @Override
        public String toString() {
            return "StoredObject[bucket=" + bucket
                    + ", key=" + key
                    + ", size=" + size
                    + ", etag=" + etag + "]";
        }
    }

    List<StoredObject> list(String bucket, String prefix, int maxObjects);

    InputStream open(String bucket, String key);

    void delete(String bucket, List<String> keys);

    /** Ghi CSV nhiều file: {@code <prefix><runId>_<n>.csv}, sang file mới khi vượt {@code maxFileBytes}. */
    MultiFileWriter openWriter(String bucket, String prefix, String runId, long maxFileBytes);

    interface MultiFileWriter extends Closeable {
        void writeLine(String csvLine) throws IOException;

        long lines();

        /** Hoàn tất mọi multipart upload; trả danh sách file đã ghi (kể cả file rỗng nếu không có dòng). */
        List<StoredObject> finish() throws IOException;

        /** Hủy các upload dở (khi lỗi). */
        void abort();
    }
}
