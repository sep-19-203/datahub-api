package vn.teca.hddt.dataapi.infrastructure.doris;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.job.RetryableJobException;
import vn.teca.hddt.dataapi.application.port.ExportPort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.MultiFileWriter;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.config.HddtProperties.Export.Engine;

/**
 * Kết xuất qua Doris. OUTFILE (mặc định): Doris BE ghi thẳng lên S3, Java chỉ nhận báo cáo (FileNumber, TotalRows,
 * FileSize, URL). JDBC_STREAM (dự phòng khi BE không tới được S3): đọc luồng đúng cột csv_line đã sinh trong SQL
 * (cùng định dạng) và ghi multipart; không giữ toàn bộ dữ liệu trong bộ nhớ.
 */
@Component
public class DorisExportAdapter implements ExportPort {
    private static final Logger log = LoggerFactory.getLogger(DorisExportAdapter.class);

    private final JdbcTemplate jdbc;
    private final ObjectStoragePort storage;
    private final HddtProperties props;
    private final ExportSqlBuilder builder;

    public DorisExportAdapter(@Qualifier("dorisExportJdbc") JdbcTemplate jdbc, ObjectStoragePort storage,
                              HddtProperties props) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.props = props;
        this.builder = new ExportSqlBuilder(new DorisTables(props.doris().database()), props.mstStatus(),
                props.hd1().formRuleEnabled(), props.hd5().formRuleEnabled(), props.doris().listServing());
    }

    @Override
    public ExportReport export(ExportSpec spec) {
        return props.export().engine() == Engine.OUTFILE ? outfile(spec) : stream(spec);
    }

    @Override
    public long countUpTo(ExportSpec spec, long limit) {
        ExportSqlBuilder.BuiltSql q = builder.countUpTo(spec, limit, props.doris().syncQueryTimeoutSeconds(), bounds(spec));
        Long n = jdbc.queryForObject(q.sql(), Long.class, q.args().toArray());
        return n == null ? 0 : n;
    }

    /** Mua vào: lấy MIN/MAX TDLAP từ HD_MST_LIST để Doris cắt partition HOADON_HDR (EXPLAIN DEV: không có thì quét mọi tháng). */
    ExportSqlBuilder.TdlapBounds bounds(ExportSpec spec) {
        if (spec.direction() != vn.teca.hddt.dataapi.domain.invoice.Direction.MUA_VAO || !builder.needsPurchaseBounds()) {
            return null;
        }
        ExportSqlBuilder.BuiltSql q = builder.purchaseTdlapBounds(spec);
        return jdbc.query(q.sql(), (ResultSetExtractor<ExportSqlBuilder.TdlapBounds>) rs -> {
            java.time.LocalDate min = rs.next() ? rs.getObject("MIN_TDLAP", java.time.LocalDate.class) : null;
            java.time.LocalDate max = min == null ? null : rs.getObject("MAX_TDLAP", java.time.LocalDate.class);
            // Không có HĐ ⇒ khoảng 1 ngày bất kỳ (kết quả rỗng vẫn đúng, vẫn cắt partition).
            return min == null ? new ExportSqlBuilder.TdlapBounds(spec.range().firstDay(), spec.range().firstDay())
                    : new ExportSqlBuilder.TdlapBounds(min, max);
        }, q.args().toArray());
    }

    private ExportReport outfile(ExportSpec spec) {
        boolean parallel = props.export().parallelOutfile();
        ExportSqlBuilder.BuiltSql q = builder.select(spec, props.doris().exportQueryTimeoutSeconds(), parallel, bounds(spec));
        String sql = q.sql() + ExportSqlBuilder.outfileClause(spec.bucket(), spec.prefix(), props.s3(), props.export());
        log.info("Job {} chạy OUTFILE ({} tham số, parallel={})", spec.jobId(), q.args().size(), parallel);
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            for (int i = 0; i < q.args().size(); i++) {
                ps.setObject(i + 1, q.args().get(i));
            }
            return ps;
        }, (ResultSetExtractor<ExportReport>) rs -> parseOutfileResult(rs, spec.bucket(), spec.prefix(), parallel));
    }

    /** Một dòng/instance; chế độ song song có nhiều dòng (nhiều URL) ⇒ URL công bố là prefix job. */
    static ExportReport parseOutfileResult(ResultSet rs, String bucket, String prefix, boolean parallel)
            throws java.sql.SQLException {
        long files = 0;
        long rows = 0;
        long bytes = 0;
        String url = null;
        int n = 0;
        ResultSetMetaData md = rs.getMetaData();
        while (rs.next()) {
            n++;
            files += rs.getLong(col(md, "FileNumber"));
            rows += rs.getLong(col(md, "TotalRows"));
            bytes += rs.getLong(col(md, "FileSize"));
            url = rs.getString(col(md, "URL"));
        }
        if (n == 0 || url == null) {
            throw new RetryableJobException("Doris OUTFILE không trả báo cáo kết quả");
        }
        String expected = "s3://" + bucket + "/";
        if (!url.startsWith(expected)) {
            throw new IllegalStateException("URL OUTFILE không thuộc bucket cấu hình: " + url);
        }
        String keyPrefix = url.substring(expected.length());
        if (keyPrefix.endsWith("*")) {
            keyPrefix = keyPrefix.substring(0, keyPrefix.length() - 1);
        }
        if (n > 1 || parallel) {
            keyPrefix = prefix;
        }
        if (!keyPrefix.startsWith(prefix)) {
            throw new IllegalStateException("URL OUTFILE nằm ngoài prefix job: " + url);
        }
        return new ExportReport(files, rows, bytes, expected + keyPrefix + "*", keyPrefix);
    }

    private static int col(ResultSetMetaData md, String label) throws java.sql.SQLException {
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (md.getColumnLabel(i).equalsIgnoreCase(label)) {
                return i;
            }
        }
        throw new IllegalStateException("Kết quả OUTFILE thiếu cột " + label);
    }

    private ExportReport stream(ExportSpec spec) {
        ExportSqlBuilder.BuiltSql q = builder.select(spec, props.doris().exportQueryTimeoutSeconds(), false, bounds(spec));
        String runId = UUID.randomUUID().toString().replace("-", "");
        MultiFileWriter writer = storage.openWriter(spec.bucket(), spec.prefix(), runId, props.export().maxFileBytes());
        try {
            jdbc.query(con -> {
                PreparedStatement ps = con.prepareStatement(q.sql(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                ps.setFetchSize(Integer.MIN_VALUE); // Connector/J: đọc luồng từng dòng
                for (int i = 0; i < q.args().size(); i++) {
                    ps.setObject(i + 1, q.args().get(i));
                }
                return ps;
            }, (RowCallbackHandler) rs -> {
                try {
                    writer.writeLine(rs.getString(1));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            List<StoredObject> files = writer.finish();
            long bytes = files.stream().mapToLong(StoredObject::size).sum();
            String keyPrefix = spec.prefix() + runId + "_";
            return new ExportReport(files.size(), writer.lines(), bytes, "s3://" + spec.bucket() + "/" + keyPrefix + "*",
                    keyPrefix);
        } catch (RuntimeException | IOException e) {
            writer.abort();
            if (e instanceof IOException) {
                throw new UncheckedIOException((IOException) e);
            }
            throw (RuntimeException) e;
        }
    }
}
