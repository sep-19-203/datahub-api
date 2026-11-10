package vn.teca.hddt.dataapi.application.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.teca.hddt.dataapi.domain.csv.CsvColumn;
import vn.teca.hddt.dataapi.domain.csv.CsvLayout;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.csv.ValueKind;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * CSV HD8 (SI-65, API v3): {@link #line} ghi một {@link InvoiceRecord} theo bố cục file HD1 v3 = đầu vào HD8;
 * {@link #resultLine} ghi một dòng kết quả {@link CsvLayouts#HD8_RESULT} — cột không hậu tố = giá trị hệ thống (Doris),
 * cột {@code *_nnt} = văn bản NNT gửi. Quy tắc văn bản theo {@link CsvText} (khớp {@code CsvLineSql} của HD1).
 */
public final class InvoiceRecordCsv {
    /** Nguồn giá trị một cột kết quả HD8. */
    private interface Cell {
        String value(Row r);
    }

    /** Một dòng kết quả: dòng đầu vào (bản ghi + văn bản gốc), hóa đơn Doris (null nếu không tồn tại / nhiều bản ghi). */
    private static final class Row {
        private final InvoiceRecord input;
        private final List<String> raw;
        private final InvoiceRecord sys;
        private final String sellerStatus;

        private Row(InvoiceRecord input, List<String> raw, InvoiceRecord sys, String sellerStatus) {
            this.input = input;
            this.raw = raw;
            this.sys = sys;
            this.sellerStatus = sellerStatus;
        }

        /** Khóa + tdlap: lấy từ hóa đơn Doris, không có thì từ file (người dùng chốt 08/11). */
        InvoiceRecord keySource() {
            return sys != null ? sys : input;
        }

        /** Văn bản NNT gửi ở cột {@code name} của file đầu vào: chuỗi/JSON bọc nháy lại, số/ngày giữ nguyên văn. */
        String nnt(String name) {
            CsvColumn c = CsvLayouts.HD8_INPUT.column(name);
            String v = raw.get(c.index() - 1);
            if (v == null || v.isEmpty()) {
                return "";
            }
            return c.kind() == ValueKind.STRING || c.kind() == ValueKind.JSON ? CsvText.str(v) : v;
        }
    }

    /**
     * Nguồn giá trị theo tên cột kết quả. Có cả cột gạch ngang còn nguồn ({@code nky}) để bật lại chỉ cần bỏ đánh dấu gạch
     * trong {@link CsvLayouts#HD8_RESULT}; 13 cột pct* không có nguồn (đã bỏ khỏi mã — SI-51) nên bật lại phải bổ sung.
     */
    private static final Map<String, Cell> RESULT_CELLS = new LinkedHashMap<>();

    static {
        RESULT_CELLS.put("nbmst", r -> CsvText.str(r.keySource().nbmst()));
        RESULT_CELLS.put("khmshdon", r -> CsvText.num(r.keySource().khmshdon()));
        RESULT_CELLS.put("khhdon", r -> CsvText.str(r.keySource().khhdon()));
        RESULT_CELLS.put("shdon", r -> CsvText.num(r.keySource().shdon()));
        RESULT_CELLS.put("tdlap", r -> CsvText.date(r.keySource().tdlap()));
        RESULT_CELLS.put("tdlap_nnt", r -> r.nnt("tdlap"));
        RESULT_CELLS.put("nky", r -> r.sys == null ? "" : CsvText.dateTime(r.sys.nky()));
        RESULT_CELLS.put("tthai", r -> r.sys == null ? "" : CsvText.num(r.sys.tthai()));
        RESULT_CELLS.put("tthai_nnt", r -> r.nnt("tthai"));
        RESULT_CELLS.put("nbten", r -> r.sys == null ? "" : CsvText.str(r.sys.nbten()));
        RESULT_CELLS.put("nbten_nnt", r -> r.nnt("nbten"));
        RESULT_CELLS.put("tgtcthue", r -> r.sys == null ? "" : CsvText.num(r.sys.tgtcthue()));
        RESULT_CELLS.put("tgtcthue_nnt", r -> r.nnt("tgtcthue"));
        RESULT_CELLS.put("tgtthue", r -> r.sys == null ? "" : CsvText.num(r.sys.tgtthue()));
        RESULT_CELLS.put("tgtthue_nnt", r -> r.nnt("tgtthue"));
        RESULT_CELLS.put("thttltsuat", r -> r.sys == null ? "" : CsvText.str(r.sys.thttltsuat()));
        RESULT_CELLS.put("thttltsuat_nnt", r -> r.nnt("thttltsuat"));
        RESULT_CELLS.put("bhphap", r -> r.sys == null ? "" : CsvText.num(r.sys.bhphap()));
        RESULT_CELLS.put("bhpldo", r -> r.sys == null ? "" : CsvText.str(r.sys.bhpldo()));
        RESULT_CELLS.put("bhpngay", r -> r.sys == null ? "" : CsvText.dateTime(r.sys.bhpngay()));
        RESULT_CELLS.put("ttmstnban", r -> CsvText.str(r.sellerStatus));
        RESULT_CELLS.put("lddctthe", r -> r.sys == null ? "" : CsvText.str(r.sys.lddctthe()));
        for (CsvColumn c : CsvLayouts.HD8_RESULT.columns()) {
            if (!RESULT_CELLS.containsKey(c.name())) {
                throw new IllegalStateException("Cột kết quả HD8 " + c.name() + " (" + c.specCell() + ") chưa có nguồn giá trị");
            }
        }
    }

    private InvoiceRecordCsv() {
    }

    /** Một dòng theo bố cục HD1 v3 15 cột (= {@link CsvLayouts#HD8_INPUT}); {@code nky} đã gạch nên không ghi. */
    public static String line(InvoiceRecord r) {
        List<String> v = new ArrayList<>(15);
        v.add(CsvText.str(r.nbmst()));
        v.add(CsvText.num(r.khmshdon()));
        v.add(CsvText.str(r.khhdon()));
        v.add(CsvText.num(r.shdon()));
        v.add(CsvText.date(r.tdlap()));
        v.add(CsvText.num(r.tthai()));
        v.add(CsvText.str(r.nbten()));
        v.add(CsvText.num(r.tgtcthue()));
        v.add(CsvText.num(r.tgtthue()));
        v.add(CsvText.str(r.thttltsuat()));
        v.add(CsvText.num(r.bhphap()));
        v.add(CsvText.str(r.bhpldo()));
        v.add(CsvText.dateTime(r.bhpngay()));
        v.add(CsvText.str(r.ttmstnban()));
        v.add(CsvText.str(r.lddctthe()));
        return String.join(",", v);
    }

    /**
     * Dòng kết quả HD8 cho một dòng đầu vào lệch.
     * @param input        bản ghi đã parse của dòng đầu vào
     * @param raw          văn bản gốc của dòng đầu vào (đúng số cột {@link CsvLayouts#HD8_INPUT})
     * @param sys          hóa đơn Doris cùng khóa; null khi không tồn tại / nhiều bản ghi ⇒ chỉ khóa + tdlap lấy từ file
     * @param sellerStatus trạng thái MST người bán (ttmstnban), tra theo nbmst
     */
    public static String resultLine(InvoiceRecord input, List<String> raw, InvoiceRecord sys, String sellerStatus) {
        if (raw.size() != CsvLayouts.HD8_INPUT.size()) {
            throw new IllegalArgumentException("Dòng đầu vào có " + raw.size() + " cột, cần " + CsvLayouts.HD8_INPUT.size());
        }
        Row row = new Row(input, raw, sys, sellerStatus);
        List<String> out = new ArrayList<>(CsvLayouts.HD8_RESULT.size());
        for (CsvColumn c : CsvLayouts.HD8_RESULT.columns()) {
            out.add(RESULT_CELLS.get(c.name()).value(row));
        }
        return String.join(",", out);
    }

    /** Tên các cột kết quả HD8 đã có nguồn giá trị (gồm cả cột gạch còn nguồn như nky) — cho test. */
    static Set<String> resultCellNames() {
        return Collections.unmodifiableSet(RESULT_CELLS.keySet());
    }

    public static CsvLayout layout() {
        return CsvLayouts.HD8_RESULT;
    }
}
