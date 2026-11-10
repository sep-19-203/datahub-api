package vn.teca.hddt.dataapi.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** Kho object trong bộ nhớ (bucket/key → byte) — chỉ cho test. */
public class InMemoryObjectStorage implements ObjectStoragePort {
    public final Map<String, byte[]> objects = new TreeMap<>();

    private static String id(String bucket, String key) {
        return bucket + "/" + key;
    }

    public void put(String bucket, String key, String content) {
        objects.put(id(bucket, key), content.getBytes(StandardCharsets.UTF_8));
    }

    public void putBytes(String bucket, String key, byte[] content) {
        objects.put(id(bucket, key), content);
    }

    public String read(String bucket, String key) {
        return new String(objects.get(id(bucket, key)), StandardCharsets.UTF_8);
    }

    @Override
    public List<StoredObject> list(String bucket, String prefix, int maxObjects) {
        List<StoredObject> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : objects.entrySet()) {
            String full = e.getKey();
            if (full.startsWith(bucket + "/" + prefix) && out.size() < maxObjects) {
                out.add(new StoredObject(bucket, full.substring(bucket.length() + 1), e.getValue().length, "etag"));
            }
        }
        return out;
    }

    @Override
    public InputStream open(String bucket, String key) {
        byte[] b = objects.get(id(bucket, key));
        if (b == null) {
            throw new IllegalStateException("không có object " + key);
        }
        return new ByteArrayInputStream(b);
    }

    @Override
    public void delete(String bucket, List<String> keys) {
        keys.forEach(k -> objects.remove(id(bucket, k)));
    }

    @Override
    public MultiFileWriter openWriter(String bucket, String prefix, String runId, long maxFileBytes) {
        return new MultiFileWriter() {
            private final List<StoredObject> done = new ArrayList<>();
            private ByteArrayOutputStream buf = new ByteArrayOutputStream();
            private int index;
            private long lines;

            @Override
            public void writeLine(String csvLine) {
                byte[] b = (csvLine + "\n").getBytes(StandardCharsets.UTF_8);
                if (buf.size() > 0 && buf.size() + b.length > maxFileBytes) {
                    complete();
                }
                buf.write(b, 0, b.length);
                lines++;
            }

            private void complete() {
                String key = prefix + runId + "_" + index++ + ".csv";
                putBytes(bucket, key, buf.toByteArray());
                done.add(new StoredObject(bucket, key, buf.size(), "etag"));
                buf = new ByteArrayOutputStream();
            }

            @Override
            public long lines() {
                return lines;
            }

            @Override
            public List<StoredObject> finish() {
                if (buf.size() > 0 || done.isEmpty()) {
                    complete();
                }
                return Jdk8Compat.listCopyOf(done);
            }

            @Override
            public void abort() {
                delete(bucket, done.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
            }

            @Override
            public void close() {
            }
        };
    }
}
