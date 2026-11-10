package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.job.JobPayloads.ListExportParams;
import vn.teca.hddt.dataapi.application.job.JobQueryService;
import vn.teca.hddt.dataapi.application.job.JobSubmissionService;
import vn.teca.hddt.dataapi.application.security.ApiPermission;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.PublicJobStatus;
import vn.teca.hddt.dataapi.support.Fixtures;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;
import vn.teca.hddt.dataapi.support.TestProps;

class JobServicesTest {
    final InMemoryJobRepository repo = new InMemoryJobRepository();
    final JobSubmissionService submit = new JobSubmissionService(repo, TestProps.defaults(), new SimpleMeterRegistry());
    final ClientPrincipal tthc = new ClientPrincipal("tthc-hthue", Jdk8Compat.setOf(ApiPermission.PURCHASE_LIST), true, Jdk8Compat.setOf());
    final ClientPrincipal other = new ClientPrincipal("other", Jdk8Compat.setOf(ApiPermission.JOB_READ), true, Jdk8Compat.setOf());

    static ListExportParams params(String tthang) {
        return new ListExportParams("MUA_VAO", Fixtures.BUYER, tthang, "02/2026", true, true);
    }

    @Test
    void newJob_isQueued_withSpecJobCode() {
        Job j = submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER);
        assertThat(j.publicStatus()).isEqualTo(PublicJobStatus.TIEP_NHAN);
        assertThat(j.jobCode()).isEqualTo("hthue.mua-vao");
        assertThat(j.state()).isEqualTo(JobState.QUEUED);
    }

    @Test
    void sameRequestIdSamePayload_returnsSameJob_noDuplicateWork() {
        Job a = submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER);
        Job b = submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER);
        assertThat(b.jobId()).isEqualTo(a.jobId());
        assertThat(repo.countActiveByClient("tthc-hthue")).isEqualTo(1);
    }

    @Test
    void sameRequestIdDifferentPayloadOrApi_isConflict() {
        submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER);
        assertThatThrownBy(() -> submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("02/2026"), Fixtures.BUYER))
                .isInstanceOf(ApiException.class).extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        assertThatThrownBy(() -> submit.submit(JobType.SALES_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void perMstActiveLimit_returns429() {
        for (int i = 0; i < 3; i++) {
            submit.submit(JobType.PURCHASE_LIST, tthc, "req-" + i, params("01/2026"), Fixtures.BUYER);
        }
        assertThatThrownBy(() -> submit.submit(JobType.PURCHASE_LIST, tthc, "req-9", params("01/2026"), Fixtures.BUYER))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void otherClientCannotSeeJob_andBadIdIsNotFound() {
        Job a = submit.submit(JobType.PURCHASE_LIST, tthc, "req-1", params("01/2026"), Fixtures.BUYER);
        JobQueryService q = new JobQueryService(repo);
        assertThat(q.get(a.jobId().toString(), tthc).jobId()).isEqualTo(a.jobId());
        assertThatThrownBy(() -> q.get(a.jobId().toString(), other))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND);
        assertThatThrownBy(() -> q.get("../etc/passwd", tthc))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.JOB_NOT_FOUND);
    }
}
