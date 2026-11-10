package vn.teca.hddt.dataapi.application.export;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.teca.hddt.dataapi.application.job.JobPayloads.ListExportParams;
import vn.teca.hddt.dataapi.application.port.ExportPort;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.PublicJobStatus;

/**
 * SI-06 (HD1!AA35/AA36, HD5!AA35/AA36): {@code url}, {@code tsdong} trả ngay "nếu số lượng hóa đơn ít &lt;10k"
 * (GiaiPhap v3.8 Bảng 2: HD1 "≤ 10.000 dòng: trả trực tiếp", HD5 "Như HD1"). Job vẫn đi qua kho job + worker như mọi job
 * (cùng kiểm tra file, manifest, idempotency); POST chỉ đếm có giới hạn rồi chờ job xong trong
 * {@code hddt.hd1|hd5.realtime-wait} (theo chiều mua vào/bán ra). Đếm lỗi, kết quả
 * lớn hoặc quá hạn chờ ⇒ trả job như cũ (TIEP_NHAN/DANG_XU_LY), client lấy kết quả bằng GET job.
 */
@Service
public class RealtimeResultWaiter {
    private static final Logger log = LoggerFactory.getLogger(RealtimeResultWaiter.class);

    private final ExportPort exporter;
    private final JobRepository jobs;
    private final HddtProperties props;

    public RealtimeResultWaiter(ExportPort exporter, JobRepository jobs, HddtProperties props) {
        this.exporter = exporter;
        this.jobs = jobs;
        this.props = props;
    }

    public Job awaitIfSmall(Job job, ListExportParams p) {
        Direction direction = Direction.valueOf(p.direction());
        HddtProperties.ListExportSettings cfg = props.listExport(direction);
        if (!cfg.realtimeEnabled() || finished(job)) {
            return job;
        }
        long rows;
        try {
            MonthRange range = MonthRange.parse(p.tthang(), p.dthang(), Integer.MAX_VALUE);
            rows = exporter.countUpTo(new ExportSpec(job.jobId(), 0, direction, p.mst(), range,
                    p.lbttmsthue(), props.mstStatus().excludedCodes(), p.lbbhphap(), null, null), cfg.realtimeRowLimit());
        } catch (RuntimeException e) {
            log.warn("Job {}: không đếm được số dòng để trả ngay, trả trạng thái job ({})", job.jobId(), e.toString());
            return job;
        }
        if (rows >= cfg.realtimeRowLimit()) {
            return job;
        }
        Instant deadline = Instant.now().plus(cfg.realtimeWait());
        Job current = job;
        while (!finished(current) && Instant.now().isBefore(deadline)) {
            if (!sleep(cfg.realtimePollInterval())) {
                break;
            }
            current = jobs.findById(job.jobId()).orElse(current);
        }
        if (!finished(current)) {
            log.info("Job {} ({} dòng) chưa xong sau {}, trả trạng thái hiện tại", job.jobId(), rows, cfg.realtimeWait());
        }
        return current;
    }

    private static boolean finished(Job j) {
        return j.publicStatus() == PublicJobStatus.HOAN_THANH || j.publicStatus() == PublicJobStatus.LOI;
    }

    private static boolean sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
