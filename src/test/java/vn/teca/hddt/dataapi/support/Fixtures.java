package vn.teca.hddt.dataapi.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/** Dữ liệu giả hoàn toàn (MST 01000000xx) — không dùng dữ liệu NNT thật. */
public final class Fixtures {
    public static final String BUYER = "0100000001";
    public static final String SELLER = "0100000002";

    private Fixtures() {
    }

    public static InvoiceRecord record(String seller, String khhdon, long shdon, String tgtcthue) {
        return new InvoiceRecord(seller, 1, khhdon, shdon, LocalDate.of(2026, 9, 15), LocalDateTime.of(2026, 9, 15, 10, 0, 0),
                1, "CONG TY GIA, \"A\"", new BigDecimal(tgtcthue), new BigDecimal("80000.000000"),
                null, null, null, "00", "INV", "id-" + shdon);
    }
}
