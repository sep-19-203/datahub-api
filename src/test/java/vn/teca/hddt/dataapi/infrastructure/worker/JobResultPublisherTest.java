package vn.teca.hddt.dataapi.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class JobResultPublisherTest {
    private SendResult<String, String> brokerAck() {
        org.apache.kafka.clients.producer.RecordMetadata metadata = new org.apache.kafka.clients.producer.RecordMetadata(
                new org.apache.kafka.common.TopicPartition("datahub-job-result-out", 1), 42L, 0, 0L, 0, 0);
        return new SendResult<>(new org.apache.kafka.clients.producer.ProducerRecord<>("datahub-job-result-out", "test"), metadata);
    }
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final JobResultPublisher publisher = new JobResultPublisher(kafka,
            "tthc-hthue", "tms-hthue", "datahub-job-result-out", "HDDT", "TTHC", "TMS");

    private Job completed(String client, boolean failed) {
        InMemoryJobRepository jobs = new InMemoryJobRepository();
        UUID id = UUID.randomUUID();
        JobType type = "tms-hthue".equals(client) ? JobType.REFUND_UPDATE : JobType.PURCHASE_LIST;
        jobs.insertIfAbsent(new NewJob(id, type, type.defaultCode(), client, "r", "h", "{}", null, 3));
        Job running = jobs.claimNext("w", Collections.singleton(type), Duration.ofMinutes(2)).get();
        if (failed) {
            jobs.markFailed(id, "w", running.attemptNo(), "HDDT-400", "internal", "File sai");
        } else {
            jobs.markSucceeded(id, "w", running.attemptNo(), new Completion(null, null,
                    type == JobType.REFUND_UPDATE ? null : "s3://hddt/result.csv", "{}", 2L, "ok"));
        }
        return jobs.get(id);
    }

    @Test
    void sendsBothRecipientsAndTerminalStatesToSameTopic(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        SettableListenableFuture<SendResult<String, String>> ack = new SettableListenableFuture<>();
        ack.set(brokerAck());
        when(kafka.send(eq("datahub-job-result-out"), anyString(), anyString())).thenReturn(ack);
        for (String client : new String[] {"tthc-hthue", "tms-hthue"}) {
            for (boolean failed : new boolean[] {false, true}) {
                Job job = completed(client, failed);
                publisher.publish(job);
                ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
                verify(kafka).send(eq("datahub-job-result-out"), eq(job.jobId().toString()), xml.capture());
                assertThat(xml.getValue()).contains("<ReceiptCode>" + (client.startsWith("tms") ? "TMS" : "TTHC") + "</ReceiptCode>",
                        "<TTJob>" + (failed ? "LOI" : "HOAN_THANH") + "</TTJob>");
                assertThat(xml.getValue()).contains("<KQKTra><TThai>" + (failed ? "0" : "1")
                        + "</TThai><MTa>" + (failed ? "Không thành công" : "Thành công") + "</MTa></KQKTra>");
                if (client.startsWith("tms") && !failed) {
                    assertThat(xml.getValue()).contains("<TSDong>2</TSDong>").doesNotContain("<URL>");
                }
            }
        }
        assertThat(output.getOut()).contains("JOB_RESULT_SEND_BEGIN", "JOB_RESULT_BROKER_ACK", "partition=1 offset=42")
                .doesNotContain("s3://hddt/result.csv", "<TDiep>");
    }

    @Test
    void brokerFailureIsReportedWithoutApplicationRetry() {
        SettableListenableFuture<SendResult<String, String>> nack = new SettableListenableFuture<>();
        nack.setException(new IllegalStateException("broker down"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(nack);
        assertThatThrownBy(() -> publisher.publish(completed("tthc-hthue", false)))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void readsSeparateTthcAndTmsReceiptCodeProperties() {
        SettableListenableFuture<SendResult<String, String>> ack = new SettableListenableFuture<>();
        ack.set(brokerAck());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(ack);
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(JobResultPublisher.class)
                .withBean("kafkaInternalTemplate", KafkaTemplate.class, () -> kafka)
                .withPropertyValues("hddt.job-result.enabled=true", "hddt.job-result.send-code=HDDT",
                        "hddt.job-result.tthc-receipt-code=TTHC-CONFIG", "hddt.job-result.tms-receipt-code=TMS-CONFIG")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobResultPublisher configured = context.getBean(JobResultPublisher.class);
                    configured.publish(completed("tthc-hthue", false));
                    configured.publish(completed("tms-hthue", false));
                    ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
                    verify(kafka, times(2)).send(eq("datahub-job-result-out"), anyString(), xml.capture());
                    assertThat(xml.getAllValues().get(0)).contains("<ReceiptCode>TTHC-CONFIG</ReceiptCode>");
                    assertThat(xml.getAllValues().get(1)).contains("<ReceiptCode>TMS-CONFIG</ReceiptCode>");
                });
    }

    @Test
    void ignoresOtherClients(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        publisher.publish(completed("other-client", false));
        verifyNoInteractions(kafka);
        assertThat(output.getOut()).contains("JOB_RESULT_SKIPPED", "reason=unmapped_client");
    }
}
