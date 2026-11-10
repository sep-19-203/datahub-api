package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.export.RealtimeResultWaiter;
import vn.teca.hddt.dataapi.application.job.JobPayloads.ListExportParams;
import vn.teca.hddt.dataapi.application.port.ExportPort;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.application.port.JobRepository;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.PublicJobStatus;
import vn.teca.hddt.dataapi.support.TestProps;

/** SI-06: POST HD1 trả url/tsdong ngay khi kết quả &lt; 10k dòng và job xong trong thời gian chờ. */
class RealtimeResultWaiterTest {
    static final UUID ID = UUID.fromString("26cbb6dc-e896-45d9-bbc6-cc1d640d091f");
    static final ListExportParams P = new ListExportParams("MUA_VAO", "0100000001", "01/2026", "03/2026", true, false);

    final ExportPort exporter = mock(ExportPort.class);
    final JobRepository jobs = mock(JobRepository.class);
    final RealtimeResultWaiter waiter = new RealtimeResultWaiter(exporter, jobs, TestProps.defaults());

    static Job job(JobState state) {
        boolean ok = state == JobState.SUCCEEDED;
        return new Job(ID, JobType.PURCHASE_LIST, "hthue.mua-vao", "tthc-hthue", "r", "h", "{}", "0100000001", state, 1, 3,
                Instant.parse("2026-10-28T02:52:19Z"), null, null, null, null, null, "hddt", "p/",
                ok ? "s3://hddt/p/x_*" : null, null, ok ? 120L : null, ok ? "Thành công" : null, null, null, null);
    }

    @Test
    void smallResult_waitsUntilJobFinished_andReturnsPublishedResult() {
        AtomicReference<ExportSpec> spec = new AtomicReference<>();
        when(exporter.countUpTo(any(), anyLong())).thenAnswer(inv -> {
            spec.set(inv.getArgument(0));
            return 120L;
        });
        when(jobs.findById(ID)).thenReturn(Optional.of(job(JobState.RUNNING)), Optional.of(job(JobState.SUCCEEDED)));

        Job out = waiter.awaitIfSmall(job(JobState.QUEUED), P);

        assertThat(out.publicStatus()).isEqualTo(PublicJobStatus.HOAN_THANH);
        assertThat(out.resultUrl()).isEqualTo("s3://hddt/p/x_*");
        assertThat(out.rowCount()).isEqualTo(120L);
        verify(exporter).countUpTo(any(), org.mockito.ArgumentMatchers.eq(10_000L));
        // Đếm cùng điều kiện lọc với job: MST, chiều, kỳ, lbttmsthue (mã 03/05/06), lbbhphap.
        assertThat(spec.get().direction()).isEqualTo(Direction.MUA_VAO);
        assertThat(spec.get().mst()).isEqualTo("0100000001");
        assertThat(spec.get().excludeMstStatus()).isTrue();
        assertThat(spec.get().excludedStatusCodes()).containsExactly("03", "05", "06");
        assertThat(spec.get().excludeIllegal()).isFalse();
    }

    @Test
    void resultAtOrAboveLimit_returnsAcceptedJobWithoutWaiting() {
        when(exporter.countUpTo(any(), anyLong())).thenReturn(10_000L);
        Job queued = job(JobState.QUEUED);
        assertThat(waiter.awaitIfSmall(queued, P)).isSameAs(queued);
        verify(jobs, never()).findById(any());
    }

    @Test
    void countFailure_fallsBackToAcceptedJob() {
        when(exporter.countUpTo(any(), anyLong())).thenThrow(new IllegalStateException("Doris timeout"));
        Job queued = job(JobState.QUEUED);
        assertThat(waiter.awaitIfSmall(queued, P)).isSameAs(queued);
        verify(jobs, never()).findById(any());
    }

    @Test
    void jobNotFinishedWithinWait_returnsCurrentStatus() {
        when(exporter.countUpTo(any(), anyLong())).thenReturn(5L);
        when(jobs.findById(ID)).thenReturn(Optional.of(job(JobState.RUNNING)));
        assertThat(waiter.awaitIfSmall(job(JobState.QUEUED), P).publicStatus()).isEqualTo(PublicJobStatus.DANG_XU_LY);
    }

    @Test
    void hd5_countsWithSalesDirection_andUsesHd5Settings() {
        ListExportParams sales = new ListExportParams("BAN_RA", "0100000002", "01/2026", "01/2026", true, true);
        AtomicReference<ExportSpec> spec = new AtomicReference<>();
        when(exporter.countUpTo(any(), anyLong())).thenAnswer(inv -> {
            spec.set(inv.getArgument(0));
            return 42L;
        });
        when(jobs.findById(ID)).thenReturn(Optional.of(job(JobState.SUCCEEDED)));
        assertThat(waiter.awaitIfSmall(job(JobState.QUEUED), sales).publicStatus()).isEqualTo(PublicJobStatus.HOAN_THANH);
        assertThat(spec.get().direction()).isEqualTo(Direction.BAN_RA);
        assertThat(spec.get().mst()).isEqualTo("0100000002");
        assertThat(spec.get().excludeIllegal()).isTrue();

        // hddt.hd5.realtime-row-limit = 0 ⇒ tắt riêng HD5, không đếm
        org.mockito.Mockito.reset(exporter);
        vn.teca.hddt.dataapi.config.HddtProperties p = TestProps.defaults();
        vn.teca.hddt.dataapi.config.HddtProperties hd5Off = new vn.teca.hddt.dataapi.config.HddtProperties(p.api(),
                p.doris(), p.dse(), p.s3(), p.mstStatus(), p.hd1(),
                new vn.teca.hddt.dataapi.config.HddtProperties.Hd5(true, 0, java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(10)),
                p.hd7(), p.hd8(), p.tms(), p.export(), p.worker(), p.security(), p.jobCodes());
        Job queued = job(JobState.QUEUED);
        assertThat(new RealtimeResultWaiter(exporter, jobs, hd5Off).awaitIfSmall(queued, sales)).isSameAs(queued);
        verify(exporter, never()).countUpTo(any(), anyLong());
    }

    @Test
    void replayOfFinishedJob_isReturnedAsIs() {
        Job done = job(JobState.SUCCEEDED);
        assertThat(waiter.awaitIfSmall(done, P)).isSameAs(done);
        verify(exporter, never()).countUpTo(any(), anyLong());
    }
}
