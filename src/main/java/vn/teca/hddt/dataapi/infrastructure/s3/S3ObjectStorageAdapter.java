package vn.teca.hddt.dataapi.infrastructure.s3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** S3/MinIO qua AWS SDK v2. Ghi theo luồng bằng multipart (phần 8 MB) — không giữ cả file trong bộ nhớ. */
@Component
public class S3ObjectStorageAdapter implements ObjectStoragePort {
    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageAdapter.class);
    static final int PART_SIZE = 8 * 1024 * 1024;

    private final S3Client s3;

    public S3ObjectStorageAdapter(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public List<StoredObject> list(String bucket, String prefix, int maxObjects) {
        List<StoredObject> out = new ArrayList<>();
        ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        for (S3Object o : s3.listObjectsV2Paginator(req).contents()) {
            out.add(new StoredObject(bucket, o.key(), o.size(), o.eTag()));
            if (out.size() >= maxObjects) {
                break;
            }
        }
        return out;
    }

    @Override
    public InputStream open(String bucket, String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /**
     * MinIO đời cũ bắt buộc Content-MD5 cho DeleteObjects (SDK ≥ 2.30 không gửi MD5 — kiểm chứng MinIO 192.168.1.69,
     * 31/10) ⇒ lần đầu bị từ chối thì chuyển hẳn sang xóa từng object.
     */
    private volatile boolean singleDeletes;

    @Override
    public void delete(String bucket, List<String> keys) {
        if (!singleDeletes) {
            try {
                for (int i = 0; i < keys.size(); i += 1000) {
                    List<ObjectIdentifier> ids = keys.subList(i, Math.min(keys.size(), i + 1000)).stream()
                            .map(k -> ObjectIdentifier.builder().key(k).build()).collect(Jdk8Compat.toList());
                    s3.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                            .delete(Delete.builder().objects(ids).quiet(true).build()).build());
                }
                return;
            } catch (S3Exception e) {
                if (e.statusCode() != 400 && e.statusCode() != 501) {
                    throw e;
                }
                log.warn("S3 từ chối DeleteObjects ({}); chuyển sang xóa từng object", e.statusCode());
                singleDeletes = true;
            }
        }
        for (String k : keys) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(k).build());
        }
    }

    @Override
    public MultiFileWriter openWriter(String bucket, String prefix, String runId, long maxFileBytes) {
        return new Writer(bucket, prefix, runId, Math.max(maxFileBytes, PART_SIZE));
    }

    final class Writer implements MultiFileWriter {
        private final String bucket;
        private final String prefix;
        private final String runId;
        private final long maxFileBytes;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream(PART_SIZE + 64 * 1024);
        private final List<StoredObject> done = new ArrayList<>();
        private final List<CompletedPart> parts = new ArrayList<>();
        private int fileIndex;
        private String uploadId;
        private long fileBytes;
        private long lines;

        Writer(String bucket, String prefix, String runId, long maxFileBytes) {
            this.bucket = bucket;
            this.prefix = prefix;
            this.runId = runId;
            this.maxFileBytes = maxFileBytes;
        }

        private String key() {
            return prefix + runId + "_" + fileIndex + ".csv";
        }

        @Override
        public void writeLine(String csvLine) throws IOException {
            byte[] b = (csvLine + "\n").getBytes(StandardCharsets.UTF_8);
            if (fileBytes > 0 && fileBytes + b.length > maxFileBytes) {
                completeFile();
            }
            buf.write(b);
            fileBytes += b.length;
            lines++;
            if (buf.size() >= PART_SIZE) {
                uploadPart();
            }
        }

        private void uploadPart() {
            if (uploadId == null) {
                uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(bucket).key(key())
                        .contentType("text/csv; charset=utf-8").build()).uploadId();
            }
            int partNumber = parts.size() + 1;
            String etag = s3.uploadPart(UploadPartRequest.builder().bucket(bucket).key(key()).uploadId(uploadId)
                    .partNumber(partNumber).build(), RequestBody.fromBytes(buf.toByteArray())).eTag();
            parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
            buf.reset();
        }

        private void completeFile() {
            String etag;
            if (uploadId == null) {
                PutObjectResponse r = s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key())
                        .contentType("text/csv; charset=utf-8").build(), RequestBody.fromBytes(buf.toByteArray()));
                etag = r.eTag();
            } else {
                if (buf.size() > 0) {
                    uploadPart();
                }
                CompleteMultipartUploadResponse r = s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                        .bucket(bucket).key(key()).uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
                etag = r.eTag();
            }
            done.add(new StoredObject(bucket, key(), fileBytes, etag));
            buf.reset();
            parts.clear();
            uploadId = null;
            fileBytes = 0;
            fileIndex++;
        }

        @Override
        public long lines() {
            return lines;
        }

        @Override
        public List<StoredObject> finish() {
            if (fileBytes > 0 || done.isEmpty()) {
                completeFile();
            }
            return Jdk8Compat.listCopyOf(done);
        }

        @Override
        public void abort() {
            try {
                if (uploadId != null) {
                    s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(key())
                            .uploadId(uploadId).build());
                }
                if (!done.isEmpty()) {
                    delete(bucket, done.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
                }
            } catch (RuntimeException e) {
                log.warn("Không hủy được upload dở dưới {}: {}", prefix, e.getMessage());
            }
        }

        @Override
        public void close() {
            // finish()/abort() là thao tác tường minh; close không tự hoàn tất để tránh công bố file dở.
        }
    }
}
