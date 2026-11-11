package vn.teca.hddt.dataapi.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;

import vn.teca.hddt.dataapi.application.port.JobRepository.Completion;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.NewJob;
import vn.teca.hddt.dataapi.support.InMemoryJobRepository;

class JobResultXmlTest {
    private final InMemoryJobRepository jobs = new InMemoryJobRepository();

    private Job running() {
        UUID id = UUID.randomUUID();
        jobs.insertIfAbsent(new NewJob(id, JobType.PURCHASE_LIST, "hthue.mua-vao", "tthc-hthue", "r", "h", "{}", null, 3));
        return jobs.claimNext("worker", Collections.singleton(JobType.PURCHASE_LIST), Duration.ofMinutes(2)).get();
    }

    private Document parse(Job job) throws Exception {
        String xml = JobResultXml.render(job, "HDDT", "TTHC", LocalDateTime.of(2026, 11, 10, 10, 15));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String value(Document doc, String tag) {
        return doc.getElementsByTagName(tag).item(0).getTextContent();
    }

    @Test
    void successfulJobContainsResultAndEscapesDescription() throws Exception {
        Job running = running();
        jobs.markSucceeded(running.jobId(), "worker", running.attemptNo(),
                new Completion("hddt", "p/", "s3://hddt/p/a&b.csv", "{}", 4L, "Đã xuất A & B"));
        Document doc = parse(jobs.get(running.jobId()));
        assertThat(value(doc, "SendCode")).isEqualTo("HDDT");
        assertThat(value(doc, "ReceiptCode")).isEqualTo("TTHC");
        assertThat(value(doc, "MJob")).isEqualTo(running.jobId().toString());
        assertThat(value(doc, "MLJob")).isEqualTo("hthue.mua-vao");
        assertThat(value(doc, "TTJob")).isEqualTo("HOAN_THANH");
        assertThat(value(doc, "URL")).isEqualTo("s3://hddt/p/a&b.csv");
        assertThat(value(doc, "TSDong")).isEqualTo("4");
        assertThat(value(doc, "TThai")).isEqualTo("1");
        assertThat(value(doc, "MTa")).isEqualTo("Đã xuất A & B");
        assertThat(doc.getElementsByTagName("KQKTra").item(0).getTextContent()).isEqualTo("1Thành công");
    }

    @Test
    void failedJobContainsSafeDescriptionWithoutResultUrl() throws Exception {
        Job running = running();
        jobs.markFailed(running.jobId(), "worker", running.attemptNo(), "HDDT-400-FILE", "internal", "File sai cấu trúc");
        Document doc = parse(jobs.get(running.jobId()));
        assertThat(value(doc, "TTJob")).isEqualTo("LOI");
        assertThat(value(doc, "MTa")).isEqualTo("File sai cấu trúc");
        assertThat(doc.getElementsByTagName("URL").getLength()).isZero();
        assertThat(doc.getElementsByTagName("TSDong").getLength()).isZero();
        assertThat(value(doc, "TThai")).isEqualTo("0");
        assertThat(doc.getElementsByTagName("KQKTra").item(0).getTextContent()).isEqualTo("0Không thành công");
    }
}
