package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.csv.CsvStreams;
import vn.teca.hddt.dataapi.application.csv.InvoiceRecordCsv;
import vn.teca.hddt.dataapi.application.csv.InvoiceRecordParser;
import vn.teca.hddt.dataapi.application.csv.RefundRowParser;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.support.Fixtures;

class CsvCodecTest {

    static List<CSVRecord> parse(String s) throws Exception {
        try (CSVParser p = CsvStreams.open(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)))) {
            return new ArrayList<>(p.getRecords());
        }
    }

    @Test
    void invoiceLine_isHd1V3_15Columns_noHeader_quotesStrings_keepsLeadingZeros_andRoundTrips() throws Exception {
        InvoiceRecord r = Fixtures.record("0100000002", "C26TAA", 99, "940000.000000");
        String line = InvoiceRecordCsv.line(r);
        assertThat(line).startsWith("\"0100000002\",1,\"C26TAA\",99,2026-09-15,1,"); // nky gạch ⇒ không có
        assertThat(line).contains("\"CONG TY GIA, \"\"A\"\"\"");
        assertThat(line).contains(",940000.000000,");
        List<CSVRecord> recs = parse(line + "\n");
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).size()).isEqualTo(15);
        InvoiceRecordParser.Parsed back = InvoiceRecordParser.parse("k", recs.get(0));
        assertThat(back.errors()).isEmpty();
        assertThat(back.record().nbten()).isEqualTo("CONG TY GIA, \"A\"");
        assertThat(back.record().tgtcthue()).isEqualByComparingTo("940000");
        assertThat(back.record().bhphap()).isNull();
        assertThat(back.record().nbmst()).isEqualTo("0100000002");
    }

    @Test
    void parser_reportsEveryFieldError_withoutValues() throws Exception {
        String bad = "\"0100000002\",X,\"C26TAA\",-1,2026-13-01,1,\"A\",1e5,1,,,,2026-09-15T10:00,\"00\",";
        InvoiceRecordParser.Parsed p = InvoiceRecordParser.parse("f.csv", parse(bad + "\n").get(0));
        assertThat(p.ok()).isFalse();
        assertThat(p.errors()).extracting(e -> e.column())
                .contains("khmshdon", "shdon", "tdlap", "tgtcthue", "bhpngay");
        assertThat(p.errors()).allSatisfy(e -> assertThat(e.reason()).doesNotContain("1e5"));
    }

    @Test
    void parser_rejectsWrongColumnCount_andMissingRequired() throws Exception {
        assertThat(InvoiceRecordParser.parse("f", parse("\"a\",1\n").get(0)).errors().get(0).reason())
                .contains("số cột 2 khác 15");
        // file HD1 bản 06/11 (14 cột, còn nky) không còn nhận (SI-65)
        String old0611 = "\"0100000002\",1,\"C26TAA\",9,2026-09-15,,1,\"A\",1,1,,,,\"00\"";
        assertThat(InvoiceRecordParser.parse("f", parse(old0611 + "\n").get(0)).errors().get(0).reason())
                .contains("số cột 14 khác 15");
        // HD8: chỉ khóa + tdlap bắt buộc (file HD1 có thể có cột rỗng, vd ttmstnban); thiếu tdlap ⇒ lỗi
        String noName = "\"0100000002\",1,\"C26TAA\",9,2026-09-15,1,,1,1,,,,,,";
        assertThat(InvoiceRecordParser.parse("f", parse(noName + "\n").get(0)).errors()).isEmpty();
        String noTdlap = "\"0100000002\",1,\"C26TAA\",9,,1,\"A\",1,1,,,,,\"00\",";
        assertThat(InvoiceRecordParser.parse("f", parse(noTdlap + "\n").get(0)).errors())
                .extracting(e -> e.column()).containsExactly("tdlap");
    }

    @Test
    void lengthIsMeasuredInUtf8Bytes_likeDorisVarchar() throws Exception {
        String khhdon = "K23TĐĐĐĐ"; // 4 + 4×2 = 12 byte > 11
        String line = "\"0100000002\",1,\"" + khhdon + "\",9,2026-09-15,1,\"A\",1,1,,,,,\"00\",";
        assertThat(InvoiceRecordParser.parse("f", parse(line + "\n").get(0)).errors())
                .extracting(e -> e.column()).containsExactly("khhdon");
    }

    @Test
    void hd8Input_keepsThttltsuatJsonAndLddcttheVerbatim() throws Exception {
        String json = "[{\"tsuat\":\"8%\",\"thtien\":940000,\"tthue\":75200}]";
        String line = "\"0100000002\",1,\"C26TAA\",9,2026-09-15,1,\"A\",940000,75200,\"" + json.replace("\"", "\"\"")
                + "\",,,,\"00\",\"Điều chỉnh, giảm \"\"giá\"\"\"";
        InvoiceRecordParser.Parsed p = InvoiceRecordParser.parse("f", parse(line + "\n").get(0));
        assertThat(p.errors()).isEmpty();
        assertThat(p.record().thttltsuat()).isEqualTo(json);
        assertThat(p.record().lddctthe()).isEqualTo("Điều chỉnh, giảm \"giá\"");
        assertThat(p.record().nky()).isNull();
    }

    @Test
    void invalidUtf8_surfacesAsError_notSilentReplacement() {
        byte[] bad = {'"', (byte) 0xC3, (byte) 0x28, '"', '\n'};
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            try (CSVParser p = CsvStreams.open(new ByteArrayInputStream(bad))) {
                p.getRecords();
            }
        }).isInstanceOfAny(UncheckedIOException.class, java.io.IOException.class);
    }

    @Test
    void bomIsSkipped() throws Exception {
        List<CSVRecord> r = parse("﻿\"0100000002\",1\n");
        assertThat(r.get(0).get(0)).isEqualTo("0100000002");
    }

    @Test
    void tmsRow_parsesJsonTaxSummary() throws Exception {
        String line = "\"0100000002\",1,\"C26TAA\",99,2026-10-28,2026-10-28 09:52:19,1,\"CTY\",940000,75200,"
                + "\"[{\"\"tsuat\"\":\"\"8%\"\",\"\"thtien\"\":940000,\"\"tthue\"\":75200}]\",\"00\",\"Điều chỉnh, giảm giá\"";
        RefundRowParser.Parsed p = RefundRowParser.parse("t.csv", parse(line + "\n").get(0));
        assertThat(p.errors()).isEmpty();
        assertThat(p.row().thttltsuat()).hasSize(1);
        assertThat(p.row().thttltsuat().get(0).tsuat()).isEqualTo("8%");
        assertThat(p.row().thttltsuat().get(0).tthue()).isEqualByComparingTo("75200");
        assertThat(p.row().lddctthe()).isEqualTo("Điều chỉnh, giảm giá");
        String oldV2 = line.substring(0, line.lastIndexOf(",\"Điều chỉnh"));
        RefundRowParser.Parsed noReason = RefundRowParser.parse("t.csv", parse(oldV2 + ",\n").get(0));
        assertThat(noReason.errors()).isEmpty();
        assertThat(noReason.row().lddctthe()).isNull();
        assertThat(RefundRowParser.parse("t.csv", parse(oldV2 + "\n").get(0)).errors().get(0).reason())
                .contains("số cột 12 khác 13");
    }

    @Test
    void tmsRow_rejectsMalformedJson() throws Exception {
        String line = "\"0100000002\",1,\"C26TAA\",99,2026-10-28,,1,\"CTY\",940000,75200,\"{not json\",\"00\",";
        RefundRowParser.Parsed p = RefundRowParser.parse("t.csv", parse(line + "\n").get(0));
        assertThat(p.errors()).extracting(e -> e.column()).containsExactly("thttltsuat");
    }
}
