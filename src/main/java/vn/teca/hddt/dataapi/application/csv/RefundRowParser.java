package vn.teca.hddt.dataapi.application.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.invoice.TaxRateLine;
import vn.teca.hddt.dataapi.domain.refund.RefundRow;

/** Parse dòng file TMS v3 13 cột (TMS!C34–C46). Cột thttltsuat là JSON mảng {tsuat, thtien, tthue} (SI-16). */
public final class RefundRowParser {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public static final class Parsed {
        private final RefundRow row;
        private final List<RowError> errors;

        public Parsed(RefundRow row, List<RowError> errors) {
            this.row = row;
            this.errors = errors;
        }

        public RefundRow row() {
            return row;
        }

        public List<RowError> errors() {
            return errors;
        }

        public boolean ok() {
            return errors.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Parsed)) {
                return false;
            }
            Parsed other = (Parsed) o;
            return Objects.equals(row, other.row)
                    && Objects.equals(errors, other.errors);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(row);
            result = 31 * result + Objects.hashCode(errors);
            return result;
        }

        @Override
        public String toString() {
            return "Parsed[row=" + row
                    + ", errors=" + errors + "]";
        }
    }

    private RefundRowParser() {
    }

    public static Parsed parse(String objectKey, CSVRecord rec) {
        CsvFieldReader f = new CsvFieldReader(CsvLayouts.TMS_REFUND_INPUT, objectKey, rec);
        if (!f.columnCountOk()) {
            return new Parsed(null, f.errors());
        }
        String nbmst = f.string("nbmst", 14);
        Integer khmshdon = f.byteValue("khmshdon");
        String khhdon = f.string("khhdon", 11);
        Long shdon = f.integer("shdon");
        LocalDate tdlap = f.date("tdlap");
        LocalDateTime nky = f.dateTime("nky");
        Integer tthai = f.byteValue("tthai");
        String nbten = f.string("nbten", 1024);
        BigDecimal tgtcthue = f.decimal("tgtcthue");
        BigDecimal tgtthue = f.decimal("tgtthue");
        String ratesJson = f.rawOptional("thttltsuat");
        List<TaxRateLine> rates = parseRates(f, ratesJson);
        String ttmst = f.string("ttmstnban", 20);
        String lddctthe = f.rawOptional("lddctthe");
        if (!f.errors().isEmpty()) {
            return new Parsed(null, f.errors());
        }
        return new Parsed(new RefundRow(rec.getRecordNumber(), nbmst, khmshdon, khhdon, shdon, tdlap, nky, tthai,
                nbten, tgtcthue, tgtthue, rates, ttmst, ratesJson, lddctthe), Jdk8Compat.listOf());
    }

    static List<TaxRateLine> parseRates(CsvFieldReader f, String json) {
        if (json == null) {
            return Jdk8Compat.listOf();
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (!node.isArray()) {
                f.fail("thttltsuat", "phải là mảng JSON");
                return Jdk8Compat.listOf();
            }
            List<TaxRateLine> out = new ArrayList<>();
            for (JsonNode e : node) {
                if (!e.isObject() || !e.hasNonNull("tsuat") || !e.get("tsuat").isTextual()) {
                    f.fail("thttltsuat", "phần tử thiếu tsuat dạng chuỗi");
                    return Jdk8Compat.listOf();
                }
                out.add(new TaxRateLine(e.get("tsuat").asText(), number(f, e.get("thtien")), number(f, e.get("tthue"))));
            }
            return out;
        } catch (Exception e) {
            f.fail("thttltsuat", "JSON không hợp lệ");
            return Jdk8Compat.listOf();
        }
    }

    private static BigDecimal number(CsvFieldReader f, JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isNumber()) {
            f.fail("thttltsuat", "thtien/tthue phải là số");
            return null;
        }
        return n.decimalValue();
    }
}
