package vn.teca.hddt.dataapi.application.csv;

import java.util.List;
import java.util.Objects;

import org.apache.commons.csv.CSVRecord;

import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Parse dòng CSV đầu vào HD8 theo bố cục file HD1 v3 15 cột (SI-12, SI-65 — {@code CsvLayouts.HD8_INPUT}). Độ dài theo DDL
 * Doris: NBMST 14, KHHDON 11, NBTEN 1024, BHPLDO 512 byte; ttmstnban đề xuất ≤ 20. File HD1 v3 không còn {@code nky} ⇒ null.
 * {@code thttltsuat} giữ nguyên văn (JSON do HD1 xuất, chỉ để ghi lại vào {@code thttltsuat_nnt}); {@code lddctthe} là
 * {@code GCHDGOC} kiểu STRING không giới hạn độ dài trên Doris ⇒ không kiểm độ dài.
 */
public final class InvoiceRecordParser {

    public static final class Parsed {
        private final InvoiceRecord record;
        private final List<RowError> errors;

        public Parsed(InvoiceRecord record, List<RowError> errors) {
            this.record = record;
            this.errors = errors;
        }

        public InvoiceRecord record() {
            return record;
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
            return Objects.equals(record, other.record)
                    && Objects.equals(errors, other.errors);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(record);
            result = 31 * result + Objects.hashCode(errors);
            return result;
        }

        @Override
        public String toString() {
            return "Parsed[record=" + record
                    + ", errors=" + errors + "]";
        }
    }

    private InvoiceRecordParser() {
    }

    public static Parsed parse(String objectKey, CSVRecord rec) {
        CsvFieldReader f = new CsvFieldReader(CsvLayouts.HD8_INPUT, objectKey, rec);
        if (!f.columnCountOk()) {
            return new Parsed(null, f.errors());
        }
        InvoiceRecord r = new InvoiceRecord(
                f.string("nbmst", 14), f.byteValue("khmshdon"), f.string("khhdon", 11), f.integer("shdon"),
                f.date("tdlap"), null, f.byteValue("tthai"), f.string("nbten", 1024),
                f.decimal("tgtcthue"), f.decimal("tgtthue"), f.byteValue("bhphap"), f.string("bhpldo", 512),
                f.dateTime("bhpngay"), f.string("ttmstnban", 20), null, null,
                f.rawOptional("thttltsuat"), f.rawOptional("lddctthe"));
        return new Parsed(f.errors().isEmpty() ? r : null, f.errors());
    }
}
