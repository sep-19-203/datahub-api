package vn.teca.hddt.dataapi.application.storage;

import java.util.Comparator;
import java.util.List;

import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** Liệt kê object đầu vào từ một {@link S3Location}, áp giới hạn số object / tổng dung lượng (chống quá tải). */
public final class InputObjects {
    private InputObjects() {
    }

    public static List<StoredObject> resolve(ObjectStoragePort storage, S3Location loc, int maxObjects, long maxTotalBytes) {
        List<StoredObject> listed = storage.list(loc.bucket(), loc.key(), maxObjects + 1);
        List<StoredObject> objects = loc.prefix()
                ? listed.stream().filter(o -> !o.key().endsWith("/")).sorted(Comparator.comparing(StoredObject::key)).collect(Jdk8Compat.toList())
                : listed.stream().filter(o -> o.key().equals(loc.key())).collect(Jdk8Compat.toList());
        if (objects.isEmpty()) {
            throw new PermanentJobException("HDDT-404-FILE", "Không tìm thấy file tại " + loc.toUri());
        }
        if (objects.size() > maxObjects) {
            throw new PermanentJobException("HDDT-413", "Số file vượt giới hạn " + maxObjects);
        }
        long total = objects.stream().mapToLong(StoredObject::size).sum();
        if (total > maxTotalBytes) {
            throw new PermanentJobException("HDDT-413", "Tổng dung lượng file vượt giới hạn " + maxTotalBytes + " byte");
        }
        return objects;
    }
}
