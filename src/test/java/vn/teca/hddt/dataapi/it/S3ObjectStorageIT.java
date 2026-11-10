package vn.teca.hddt.dataapi.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.MultiFileWriter;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.infrastructure.s3.S3ObjectStorageAdapter;

/** S3/MinIO thật: multipart nhiều phần, sang file mới theo dung lượng, đọc luồng, xóa, hủy upload dở. */
class S3ObjectStorageIT {
    static S3Client s3;
    static S3ObjectStorageAdapter storage;
    static final String BUCKET = "hddt-it-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void bucket() {
        s3 = ItInfra.s3();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        storage = new S3ObjectStorageAdapter(s3);
    }

    @AfterAll
    static void cleanup() {
        if (s3 != null) {
            List<StoredObject> all = storage.list(BUCKET, "", 10_000);
            if (!all.isEmpty()) {
                storage.delete(BUCKET, all.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
            }
            s3.deleteBucket(b -> b.bucket(BUCKET));
            s3.close();
        }
    }

    @Test
    void largeStreamIsSplitIntoMultipartFiles_andReadsBackExactly() throws Exception {
        String line = "\"0100000002\",1,\"C26TAA\"," + String.join("", java.util.Collections.nCopies(180, "9")); // ~200 byte/dòng
        MultiFileWriter w = storage.openWriter(BUCKET, "export/query-result/j1/", "run1", 8L * 1024 * 1024);
        int n = 100_000; // ~20 MB ⇒ 3 file, mỗi file multipart
        for (int i = 0; i < n; i++) {
            w.writeLine(line);
        }
        List<StoredObject> files = w.finish();
        assertThat(files).hasSize(3);
        assertThat(files).allSatisfy(f -> assertThat(f.key()).startsWith("export/query-result/j1/run1_").endsWith(".csv"));
        assertThat(w.lines()).isEqualTo(n);
        long total = 0;
        for (StoredObject f : storage.list(BUCKET, "export/query-result/j1/run1_", 100)) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(storage.open(BUCKET, f.key()), StandardCharsets.UTF_8))) {
                total += r.lines().peek(l -> assertThat(l).isEqualTo(line)).count();
            }
        }
        assertThat(total).isEqualTo(n);
        assertThat(files.stream().mapToLong(StoredObject::size).sum()).isEqualTo((long) n * (line.length() + 1));
    }

    @Test
    void emptyResultStillProducesOneEmptyFile() throws Exception {
        MultiFileWriter w = storage.openWriter(BUCKET, "export/hd8-result/j2/", "run2", 8L * 1024 * 1024);
        List<StoredObject> files = w.finish();
        assertThat(files).hasSize(1);
        assertThat(storage.list(BUCKET, "export/hd8-result/j2/", 10)).singleElement()
                .satisfies(o -> assertThat(o.size()).isZero());
    }

    @Test
    void abortRemovesPartialOutput_andMissingObjectFails() throws Exception {
        MultiFileWriter w = storage.openWriter(BUCKET, "export/query-result/j3/", "run3", 8L * 1024 * 1024);
        for (int i = 0; i < 60_000; i++) {
            w.writeLine(String.join("", java.util.Collections.nCopies(200, "x"))); // > 8 MB ⇒ đã có file hoàn tất + multipart dở
        }
        w.abort();
        assertThat(storage.list(BUCKET, "export/query-result/j3/", 10)).isEmpty();
        assertThatThrownBy(() -> storage.open(BUCKET, "khong-ton-tai.csv").read()).isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void utf8ContentPreserved() throws Exception {
        MultiFileWriter w = storage.openWriter(BUCKET, "export/query-result/j4/", "run4", 8L * 1024 * 1024);
        w.writeLine("\"CÔNG TY TNHH ĐẦU TƯ \"\"Á\"\"\",\"K23TĐA\"");
        w.finish();
        String key = storage.list(BUCKET, "export/query-result/j4/", 10).get(0).key();
        String back = new String(Jdk8Compat.readAllBytes(storage.open(BUCKET, key)), StandardCharsets.UTF_8);
        assertThat(back).isEqualTo("\"CÔNG TY TNHH ĐẦU TƯ \"\"Á\"\"\",\"K23TĐA\"\n");
    }
}
