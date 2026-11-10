package vn.teca.hddt.dataapi.application.job;

import java.util.UUID;

import org.springframework.stereotype.Service;

import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.domain.job.Job;

/** GET job: chỉ client tạo job mới xem được; job của client khác trả 404 (không lộ sự tồn tại). */
@Service
public class JobQueryService {
    private final JobRepository jobs;

    public JobQueryService(JobRepository jobs) {
        this.jobs = jobs;
    }

    public Job get(String jobId, ClientPrincipal client) {
        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.JOB_NOT_FOUND);
        }
        return jobs.findById(id)
                .filter(j -> j.clientId().equals(client.clientId()))
                .orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND));
    }
}
