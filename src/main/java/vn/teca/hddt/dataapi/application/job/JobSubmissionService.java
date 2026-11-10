package vn.teca.hddt.dataapi.application.job;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.TaxCode;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;

/**
 * Tiếp nhận job bất đồng bộ với idempotency (SI-36): (client theo x-partner, X-Request-Id) duy nhất; cùng payload ⇒ trả lại job cũ,
 * khác payload ⇒ 409. Giới hạn job đang hoạt động theo client và theo MST.
 */
@Service
public class JobSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(JobSubmissionService.class);

    private final JobRepository jobs;
    private final HddtProperties props;
    private final MeterRegistry metrics;

    public JobSubmissionService(JobRepository jobs, HddtProperties props, MeterRegistry metrics) {
        this.jobs = jobs;
        this.props = props;
        this.metrics = metrics;
    }

    public Job submit(JobType type, ClientPrincipal client, String requestId, Object params, String subjectMst) {
        String payload = JobPayloads.toJson(params);
        String hash = JobPayloads.sha256(type.name() + "\n" + payload);

        Optional<Job> existing = jobs.findByClientRequest(client.clientId(), requestId);
        if (existing.isPresent()) {
            return replay(existing.get(), hash, type);
        }
        if (jobs.countActiveByClient(client.clientId()) >= props.api().maxActiveJobsPerClient()) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "Client đã đạt số job đang xử lý tối đa");
        }
        if (subjectMst != null && jobs.countActiveBySubject(subjectMst) >= props.api().maxActiveJobsPerMst()) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "MST đã có số job đang xử lý tối đa");
        }
        NewJob nj = new NewJob(UUID.randomUUID(), type, props.jobCode(type), client.clientId(), requestId, hash, payload,
                subjectMst, props.worker().maxAttempts());
        Optional<Job> inserted = jobs.insertIfAbsent(nj);
        if (!inserted.isPresent()) {
            // Hai request cùng X-Request-Id tới đồng thời: bên thua đọc lại bản đã ghi.
            Job winner = jobs.findByClientRequest(client.clientId(), requestId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Không ghi được job"));
            return replay(winner, hash, type);
        }
        Job job = inserted.get();
        jobs.recordEvent(job.jobId(), "SUBMITTED", 0, null, null);
        metrics.counter("hddt.job.submitted", "type", type.name()).increment();
        log.info("Tiếp nhận job {} type={} client={} mst={}", job.jobId(), type, client.clientId(),
                subjectMst == null ? "-" : TaxCode.mask(subjectMst));
        return job;
    }

    private Job replay(Job existing, String hash, JobType type) {
        if (!existing.requestHash().equals(hash) || existing.type() != type) {
            metrics.counter("hddt.job.idempotency.conflict").increment();
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        metrics.counter("hddt.job.idempotency.replay", "type", type.name()).increment();
        return existing;
    }
}
