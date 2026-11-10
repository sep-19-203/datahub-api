package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.export.ListExportHandler;
import vn.teca.hddt.dataapi.application.job.JobContext;
import vn.teca.hddt.dataapi.application.job.JobPayloads.ListExportParams;
import vn.teca.hddt.dataapi.application.job.PermanentJobException;
import vn.teca.hddt.dataapi.application.job.RetryableJobException;
import vn.teca.hddt.dataapi.application.port.ExportPort;
import vn.teca.hddt.dataapi.application.port.ExportPort.ExportReport;
import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.support.Fixtures;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;
import vn.teca.hddt.dataapi.support.InMemoryObjectStorage;
import vn.teca.hddt.dataapi.support.TestProps;

class ListExportHandlerTest {
    final InMemoryJobRepository repo = new InMemoryJobRepository();
    final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    final TaxpayerStatusPort statuses = mock(TaxpayerStatusPort.class);
    final AtomicReference<ExportPort.ExportSpec> lastSpec = new AtomicReference<>();
    /** HD1 v3: 15 cột; thttltsuat là JSON bọc nháy kép (chứa dấu phẩy) — bộ đếm phải parse CSV, không đếm dấu phẩy. */
    static final String LINE = "\"0100000002\",1,\"C26TAA\",9,2026-09-15,1,\"A\",1.000000,0.100000,"
            + "\"[{\"\"tsuat\"\":\"\"10%\"\",\"\"thtien\"\":1,\"\"tthue\"\":0.1}]\",,,,\"00\",\"Thay thế do sai tên\"";

    /** Doris giả: ghi 2 file theo quy ước tên OUTFILE và báo cáo số liệu (có thể sai lệch có chủ đích). */
    ExportPort doris(int rows, long reportedRows, long reportedFiles) {
        return spec -> {
            lastSpec.set(spec);
            String id = "c852ec478bc8afffb49e7-a82661eec9d995ad";
            StringBuilder a = new StringBuilder();
            for (int i = 0; i < rows; i++) {
                a.append(LINE).append('\n');
            }
            storage.put("hddt", spec.prefix() + id + "_0.csv", a.toString());
            storage.put("hddt", spec.prefix() + id + "_1.csv", "");
            storage.put("hddt", spec.prefix() + "_SUCCESS", "");
            long bytes = a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return new ExportReport(reportedFiles, reportedRows, bytes, "s3://hddt/" + spec.prefix() + id + "_*",
                    spec.prefix() + id + "_");
        };
    }

    JobContext ctx(String direction) {
        return HandlerTestSupport.claimed(repo, JobType.PURCHASE_LIST,
                new ListExportParams(direction, Fixtures.BUYER, "01/2026", "03/2026", true, false));
    }

    @Test
    void verifiedExport_publishesSpecStyleUrl_rowCount_andManifest_andCleansEarlierAttempt() throws Exception {
        when(statuses.configured()).thenReturn(true);
        JobContext c = ctx("MUA_VAO");
        storage.put("hddt", "export/query-result/" + c.jobId() + "/old-attempt_0.csv", "stale");
        Completion done = new ListExportHandler(doris(3, 3, 2), storage, statuses, TestProps.defaults()).execute(c);
        assertThat(done.resultUrl()).isEqualTo("s3://hddt/export/query-result/" + c.jobId()
                + "/c852ec478bc8afffb49e7-a82661eec9d995ad_*");
        assertThat(done.rowCount()).isEqualTo(3);
        assertThat(done.manifestJson()).contains("\"columns\":15").contains("\"header\":false");
        assertThat(storage.objects.keySet()).noneMatch(k -> k.contains("old-attempt"));
        assertThat(lastSpec.get().direction()).isEqualTo(Direction.MUA_VAO);
        assertThat(lastSpec.get().excludedStatusCodes()).containsExactly("03", "05", "06");
        assertThat(lastSpec.get().range().months()).isEqualTo(3);
    }

    @Test
    void fileCountMismatch_isRetryable_neverPublished() {
        when(statuses.configured()).thenReturn(true);
        assertThatThrownBy(() -> new ListExportHandler(doris(3, 3, 5), storage, statuses, TestProps.defaults())
                .execute(ctx("MUA_VAO"))).isInstanceOf(RetryableJobException.class);
    }

    @Test
    void recordCountMismatch_failsInsteadOfReportingSuccess() {
        when(statuses.configured()).thenReturn(true);
        assertThatThrownBy(() -> new ListExportHandler(doris(3, 4, 2), storage, statuses, TestProps.defaults())
                .execute(ctx("MUA_VAO"))).isInstanceOf(PermanentJobException.class).hasMessageContaining("khác số dòng");
    }

    @Test
    void wrongColumnCountForLayout_isDetected() {
        when(statuses.configured()).thenReturn(true);
        // file 15 cột (HD1 v3) nhưng job HD5 cần 18 cột (sheet 08/11: thttltsuat, lddctthe)
        assertThatThrownBy(() -> new ListExportHandler(doris(1, 1, 2), storage, statuses, TestProps.defaults())
                .execute(ctx("BAN_RA"))).isInstanceOf(PermanentJobException.class).hasMessageContaining("cần 18");
    }

    @Test
    void withoutStatusSource_jobFailsPermanently() {
        when(statuses.configured()).thenReturn(false);
        assertThatThrownBy(() -> new ListExportHandler(doris(1, 1, 2), storage, statuses, TestProps.defaults())
                .execute(ctx("MUA_VAO"))).isInstanceOf(PermanentJobException.class);
    }
}
