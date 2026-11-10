package vn.teca.hddt.dataapi.domain.verify;

import java.util.function.Function;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/** Trường có thể so sánh trong HD8 (tên = tên cột CSV HD8). Khóa (nbmst, khmshdon, khhdon, shdon) không nằm ở đây. */
public enum CompareField {
    TDLAP("tdlap", InvoiceRecord::tdlap, false),
    NKY("nky", InvoiceRecord::nky, false),
    TTHAI("tthai", InvoiceRecord::tthai, false),
    NBTEN("nbten", InvoiceRecord::nbten, false),
    TGTCTHUE("tgtcthue", InvoiceRecord::tgtcthue, true),
    TGTTHUE("tgtthue", InvoiceRecord::tgtthue, true),
    BHPHAP("bhphap", InvoiceRecord::bhphap, false),
    BHPLDO("bhpldo", InvoiceRecord::bhpldo, false),
    BHPNGAY("bhpngay", InvoiceRecord::bhpngay, false),
    TTMSTNBAN("ttmstnban", InvoiceRecord::ttmstnban, false);

    private final String csvName;
    private final Function<InvoiceRecord, Object> accessor;
    private final boolean money;

    CompareField(String csvName, Function<InvoiceRecord, Object> accessor, boolean money) {
        this.csvName = csvName;
        this.accessor = accessor;
        this.money = money;
    }

    public String csvName() {
        return csvName;
    }

    public Object value(InvoiceRecord r) {
        return accessor.apply(r);
    }

    public boolean money() {
        return money;
    }

    public static CompareField fromCsvName(String name) {
        for (CompareField f : values()) {
            if (f.csvName.equals(name)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Không so sánh được trường: " + name);
    }
}
