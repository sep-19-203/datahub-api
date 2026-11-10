package vn.teca.hddt.dataapi.infrastructure.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort;
import vn.teca.hddt.dataapi.application.port.ObjectStoragePort.StoredObject;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;

/** Dọn file kết quả quá hạn lưu trữ (SI-38): xóa object dưới prefix job rồi đánh dấu expired (GET job thôi trả url). */
@Component
@ConditionalOnProperty(prefix = "hddt.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResultRetentionJanitor {
    private static final Logger log = LoggerFactory.getLogger(ResultRetentionJanitor.class);

    private final JobRepository jobs;
    private final ObjectStoragePort storage;
    private final HddtProperties props;

    public ResultRetentionJanitor(JobRepository jobs, ObjectStoragePort storage, HddtProperties props) {
        this.jobs = jobs;
        this.storage = storage;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${hddt.export.janitor-interval:PT1H}", initialDelayString = "PT5M")
    public void sweep() {
        Instant before = Instant.now().minus(Duration.ofDays(props.export().retentionDays()));
        List<Job> expired = jobs.findExpiredResults(before, 100);
        for (Job j : expired) {
            try {
                if (j.resultBucket() != null && j.resultPrefix() != null) {
                    List<StoredObject> objs = storage.list(j.resultBucket(), j.resultPrefix(), 100_000);
                    storage.delete(j.resultBucket(), objs.stream().map(StoredObject::key).collect(Jdk8Compat.toList()));
                }
                jobs.markExpired(j.jobId());
                jobs.recordEvent(j.jobId(), "EXPIRED", j.attemptNo(), null, null);
            } catch (RuntimeException e) {
                log.warn("Không dọn được kết quả job {}: {}", j.jobId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Đã dọn {} kết quả quá hạn", expired.size());
        }
    }
}
