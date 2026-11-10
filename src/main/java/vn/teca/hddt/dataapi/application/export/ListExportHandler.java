package vn.teca.hddt.dataapi.application.export;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.csv.CsvStreams;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobHandler;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.application.job.JobPayloads.ListExportParams;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.job.RetryableJobException;
import vn.teca.hddt.dataapi.application.port.ExportPort;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportReport;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.csv.CsvLayout;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.job.JobType;

/**
 * HD1/HD5 — kết xuất CSV lên S3 bằng Doris (không kéo dữ liệu về Java ở chế độ OUTFILE). Quy trình:
 * dọn object của lần thử trước dưới prefix job → xuất → liệt kê và đối chiếu số file/byte với báo cáo engine →
 * (kết quả nhỏ) đọc lại đếm bản ghi + số cột → manifest → mới trả về để worker công bố HOAN_THANH.
 * File xuất hiện trên MinIO KHÔNG đồng nghĩa job thành công.
 */
@Component
public class ListExportHandler implements JobHandler {
    private static final Logger log = LoggerFactory.getLogger(ListExportHandler.class);

    private final ExportPort exporter;
    private final ObjectStoragePort storage;
    private final TaxpayerStatusPort statuses;
    private final HddtProperties props;

    public ListExportHandler(ExportPort exporter, ObjectStoragePort storage, TaxpayerStatusPort statuses,
                             HddtProperties props) {
        this.exporter = exporter;
        this.storage = storage;
        this.statuses = statuses;
        this.props = props;
    }

    @Override
    public Set<JobType> types() {
        return Jdk8Compat.setOf(JobType.PURCHASE_LIST, JobType.SALES_LIST);
    }

    @Override
    public Completion execute(JobContext ctx) throws IOException {
        if (!statuses.configured()) {
            throw new PermanentJobException("HDDT-503-MST-STATUS", "Nguồn trạng thái MST chưa được cấu hình (SI-24)");
        }
        ListExportParams p = JobPayloads.fromJson(ctx.job().payloadJson(), ListExportParams.class);
        Direction direction = Direction.valueOf(p.direction());
        MonthRange range = MonthRange.parse(p.tthang(), p.dthang(), Integer.MAX_VALUE);
        String bucket = props.s3().exportBucket();
        String prefix = props.s3().exportPrefix() + "/" + ctx.jobId() + "/";

        cleanup(bucket, prefix);
        ctx.progress("{\"phase\":\"EXPORTING\"}");
        ExportReport report = exporter.export(new ExportSpec(ctx.jobId(), ctx.attemptNo(), direction, p.mst(), range,
                p.lbttmsthue(), props.mstStatus().excludedCodes(), p.lbbhphap(), bucket, prefix));
        ctx.ensureOwned();
        ctx.progress("{\"phase\":\"VERIFYING\"}");

        CsvLayout layout = direction == Direction.MUA_VAO ? CsvLayouts.HD1_PURCHASE : CsvLayouts.HD5_SALES;
        List<StoredObject> data = verify(bucket, prefix, report, layout);
        String manifest = manifest(ctx, report, data, layout);
        log.info("Job {} xuất {} dòng / {} file", ctx.jobId(), report.rowCount(), data.size());
        String mta = report.rowCount() == 0 ? "Thành công - không có hóa đơn thỏa mãn điều kiện" : "Thành công";
        return new Completion(bucket, prefix, report.url(), manifest, report.rowCount(), mta);
    }

    private void cleanup(String bucket, String prefix) {
        List<StoredObject> leftovers = storage.list(bucket, prefix, 10_000);
        if (!leftovers.isEmpty()) {
            storage.delete(bucket, leftovers.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
            log.info("Đã xóa {} object của lần thử trước dưới {}", leftovers.size(), prefix);
        }
    }

    List<StoredObject> verify(String bucket, String prefix, ExportReport report, CsvLayout layout) throws IOException {
        List<StoredObject> all = storage.list(bucket, prefix, 100_000);
        List<StoredObject> data = all.stream().filter(o -> o.key().startsWith(report.dataKeyPrefix())).collect(Jdk8Compat.toList());
        if (data.size() != report.fileCount()) {
            throw new RetryableJobException("Số file trên S3 (" + data.size() + ") khác số file Doris báo ("
                    + report.fileCount() + ")");
        }
        long bytes = data.stream().mapToLong(StoredObject::size).sum();
        if (report.totalBytes() >= 0 && bytes != report.totalBytes()) {
            throw new RetryableJobException("Dung lượng trên S3 (" + bytes + ") khác dung lượng Doris báo ("
                    + report.totalBytes() + ")");
        }
        if (bytes <= props.export().verifyRecordCountMaxBytes()) {
            long records = 0;
            for (StoredObject o : data) {
                records += countRecords(o, layout);
            }
            if (records != report.rowCount()) {
                throw new PermanentJobException("HDDT-500-EXPORT", "Số bản ghi trong file (" + records
                        + ") khác số dòng truy vấn (" + report.rowCount() + ")");
            }
        }
        return data;
    }

    private long countRecords(StoredObject o, CsvLayout layout) throws IOException {
        long n = 0;
        try (InputStream in = storage.open(o.bucket(), o.key()); CSVParser parser = CsvStreams.open(in)) {
            for (CSVRecord rec : parser) {
                if (rec.size() != layout.size()) {
                    throw new PermanentJobException("HDDT-500-EXPORT", "File " + o.key() + " bản ghi "
                            + rec.getRecordNumber() + " có " + rec.size() + " cột, cần " + layout.size());
                }
                n++;
            }
        }
        return n;
    }

    private String manifest(JobContext ctx, ExportReport report, List<StoredObject> data, CsvLayout layout) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", ctx.jobId().toString());
        m.put("attempt", ctx.attemptNo());
        m.put("layout", layout.name());
        m.put("columns", layout.size());
        m.put("header", false);
        m.put("engine", props.export().engine().name());
        m.put("url", report.url());
        m.put("rowCount", report.rowCount());
        m.put("totalBytes", data.stream().mapToLong(StoredObject::size).sum());
        m.put("files", data.stream().map(o -> Jdk8Compat.mapOf("key", o.key(), "size", o.size(),
                "etag", o.etag() == null ? "" : o.etag())).collect(Jdk8Compat.toList()));
        return JobPayloads.toJson(m);
    }
}
