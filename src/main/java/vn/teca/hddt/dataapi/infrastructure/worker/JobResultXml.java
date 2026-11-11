package vn.teca.hddt.dataapi.infrastructure.worker;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobState;

/** XML kết quả job theo Chuan_XML_nhan_KQ_JOB.docx. */
final class JobResultXml {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private JobResultXml() {
    }

    static String render(Job job, String sendCode, String receiptCode, LocalDateTime sentAt) {
        if (job.state() != JobState.SUCCEEDED && job.state() != JobState.FAILED) {
            throw new IllegalArgumentException("Job chưa có kết quả cuối: " + job.jobId());
        }
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("TDiep");
            xml.writeStartElement("TTChung");
            element(xml, "SendCode", sendCode);
            element(xml, "ReceiptCode", receiptCode);
            element(xml, "TGGui", TIME.format(sentAt));
            xml.writeEndElement();
            xml.writeStartElement("DLieu");
            xml.writeStartElement("TTHCDLieu");
            xml.writeStartElement("KQJob");
            element(xml, "MJob", job.jobId().toString());
            element(xml, "MLJob", job.jobCode());
            element(xml, "TTJob", job.publicStatus().name());
            element(xml, "MTa", job.description());
            if (job.finishedAt() != null) {
                element(xml, "TDKXuat", TIME.format(LocalDateTime.ofInstant(job.finishedAt(), DateFormats.VN)));
            }
            if (job.resultPublished()) {
                element(xml, "URL", job.resultUrl());
                element(xml, "TSDong", job.rowCount() == null ? null : job.rowCount().toString());
            }
            xml.writeEndElement(); // KQJob
            xml.writeEndElement(); // TTHCDLieu
            xml.writeStartElement("KQKTra");
            boolean succeeded = job.state() == JobState.SUCCEEDED;
            element(xml, "TThai", succeeded ? "1" : "0");
            element(xml, "MTa", succeeded ? "Thành công" : "Không thành công");
            xml.writeEndElement();
            xml.writeEndElement(); // DLieu
            xml.writeEndElement(); // TDiep
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Không tạo được XML kết quả job " + job.jobId(), e);
        }
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        if (value != null) {
            xml.writeStartElement(name);
            xml.writeCharacters(value);
            xml.writeEndElement();
        }
    }
}
