package vn.teca.hddt.dataapi.infrastructure.worker;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.job.Job;

@Component
@ConditionalOnProperty(prefix = "hddt.job-result", name = "enabled", havingValue = "true")
public class JobResultPublisher {
    private static final Logger log = LoggerFactory.getLogger(JobResultPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final String tthcClientId;
    private final String tmsClientId;
    private final String topic;
    private final String sendCode;
    private final String tthcReceiptCode;
    private final String tmsReceiptCode;

    public JobResultPublisher(@Qualifier("kafkaInternalTemplate") KafkaTemplate<String, String> kafka,
            @Value("${hddt.job-result.client-id:tthc-hthue}") String tthcClientId,
            @Value("${hddt.job-result.tms-client-id:tms-hthue}") String tmsClientId,
            @Value("${hddt.job-result.topic:datahub-job-result-out}") String topic,
            @Value("${hddt.job-result.send-code:}") String sendCode,
            @Value("${hddt.job-result.tthc-receipt-code:}") String tthcReceiptCode,
            @Value("${hddt.job-result.tms-receipt-code:}") String tmsReceiptCode) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(sendCode)
                || !StringUtils.hasText(tthcReceiptCode) || !StringUtils.hasText(tmsReceiptCode)
                || !StringUtils.hasText(tthcClientId) || !StringUtils.hasText(tmsClientId)
                || tthcClientId.equals(tmsClientId)) {
            throw new IllegalArgumentException("Thiếu cấu hình topic/mã hệ thống cho kết quả job");
        }
        this.kafka = kafka;
        this.tthcClientId = tthcClientId;
        this.tmsClientId = tmsClientId;
        this.topic = topic;
        this.sendCode = sendCode;
        this.tthcReceiptCode = tthcReceiptCode;
        this.tmsReceiptCode = tmsReceiptCode;
        log.info("JOB_RESULT_PUBLISHER_READY topic={} tthcClientId={} tthcReceiptCode={} tmsClientId={} tmsReceiptCode={}",
                topic, tthcClientId, tthcReceiptCode, tmsClientId, tmsReceiptCode);
    }

    public void publish(Job job) throws Exception {
        String receiptCode = tthcClientId.equals(job.clientId()) ? tthcReceiptCode
                : tmsClientId.equals(job.clientId()) ? tmsReceiptCode : null;
        if (receiptCode == null) {
            log.warn("JOB_RESULT_SKIPPED jobId={} clientId={} reason=unmapped_client expectedTthc={} expectedTms={}",
                    job.jobId(), job.clientId(), tthcClientId, tmsClientId);
            return;
        }
        String xml = JobResultXml.render(job, sendCode, receiptCode, LocalDateTime.now(DateFormats.VN));
        // shortcut: không lưu hàng chờ; process dừng/lỗi gửi cần xử lý thủ công.
        long started = System.nanoTime();
        log.info("JOB_RESULT_SEND_BEGIN jobId={} topic={} key={} clientId={} receiptCode={} state={} xmlChars={}",
                job.jobId(), topic, job.jobId(), job.clientId(), receiptCode, job.state(), xml.length());
        SendResult<String, String> result = kafka.send(topic, job.jobId().toString(), xml).get(10, TimeUnit.SECONDS);
        RecordMetadata metadata = result.getRecordMetadata();
        log.info("JOB_RESULT_BROKER_ACK jobId={} topic={} partition={} offset={} elapsedMs={}; không phải xác nhận consumer đã xử lý",
                job.jobId(), metadata.topic(), metadata.partition(), metadata.offset(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }
}
