package vn.teca.hddt.dataapi.application.job;

import java.util.Set;

import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.domain.job.JobType;

/** Xử lý một loại job. Chỉ trả về khi kết quả đã được kiểm tra đầy đủ; worker mới công bố HOAN_THANH. */
public interface JobHandler {
    Set<JobType> types();

    JobRepository.Completion execute(JobContext ctx) throws Exception;
}
