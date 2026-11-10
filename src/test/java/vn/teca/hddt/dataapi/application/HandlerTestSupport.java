package vn.teca.hddt.dataapi.application;

import java.time.Duration;
import java.util.UUID;

import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;

final class HandlerTestSupport {
    private HandlerTestSupport() {
    }

    /** Tạo job và nhận nó như worker "w1" — trả ngữ cảnh lần thử 1. */
    static JobContext claimed(InMemoryJobRepository repo, JobType type, Object params) {
        UUID id = UUID.randomUUID();
        repo.insertIfAbsent(new NewJob(id, type, type.defaultCode(), "tthc-hthue", "r-" + id, "h",
                JobPayloads.toJson(params), null, 3));
        Job j = repo.claimNext("w1", vn.teca.hddt.dataapi.domain.common.Jdk8Compat.listOf(type), Duration.ofMinutes(2)).get();
        return new JobContext(j, repo, "w1", Duration.ofMinutes(2));
    }
}
