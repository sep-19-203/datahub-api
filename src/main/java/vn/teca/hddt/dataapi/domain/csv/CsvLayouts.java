package vn.teca.hddt.dataapi.domain.csv;

import static vn.teca.hddt.dataapi.domain.csv.ValueKind.BYTE;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.DATE;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.DATETIME;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.DECIMAL;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.INTEGER;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.JSON;
import static vn.teca.hddt.dataapi.domain.csv.ValueKind.STRING;

import java.util.ArrayList;
import java.util.List;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Bố cục CSV đúng thứ tự trong workbook `API HDDT - TTHC - TMS.xlsx`. Mọi thay đổi phải đối chiếu lại ô nguồn.
 * Test {@code CsvLayoutsTest} khóa số cột và tên từng cột.
 */
public final class CsvLayouts {

    /**
     * Số dòng đặc tả pctkct_sotien … pctkhac_sothue (13 trường số tiền theo thuế suất) đã bị gạch khỏi workbook (06/11):
     * không còn là cột CSV nhưng các dòng vẫn nằm trong sheet ⇒ chỉ dùng để giữ đúng địa chỉ ô nguồn của các cột sau.
     */
    private static final int STRUCK_PCT_ROWS = 13;

    /** Đánh dấu dòng đặc tả gạch ngang trong {@link #build}: khai báo cột nhưng không xuất (xem {@link CsvColumn#struck()}). */
    private static final String STRUCK = "STRUCK";

    /**
     * HD1 theo `API HDDT - TTHC - TMS_v3_20261106.xlsx` (SI-52): HD1!C42–C70, 15 cột, không header. So với bản 06/11: dòng
     * {@code nky} (C47) gạch ngang ⇒ bỏ; {@code thttltsuat} (C52, tô xanh) đưa lại — JSON nguyên văn như HD3; 13 dòng pct*
     * (C53–C65) gạch; {@code lddctthe} (C70) mới.
     */
    public static final CsvLayout HD1_PURCHASE = new CsvLayout("HD1", build("HD1", 42, new Object[][]{
            {"nbmst", STRING, true}, {"khmshdon", BYTE, true}, {"khhdon", STRING, true}, {"shdon", INTEGER, true},
            {"tdlap", DATE, true},
            {1}, // C47 nky — gạch ngang trong v3
            {"tthai", BYTE, true}, {"nbten", STRING, true},
            {"tgtcthue", DECIMAL, true}, {"tgtthue", DECIMAL, true},
            {"thttltsuat", JSON, false},
            {STRUCK_PCT_ROWS}, // C53–C65: 13 dòng pct*_sotien/sothue đã gạch khỏi đặc tả — không phải cột CSV
            {"bhphap", BYTE, false}, {"bhpldo", STRING, false},
            {"bhpngay", DATETIME, false}, {"ttmstnban", STRING, true}, {"lddctthe", STRING, false}}));

    /**
     * HD5!C42–C72 (Google Sheet sửa 08/11 09:36, SI-63): 18 cột, không header. 13 dòng pct* (C55–C67) gạch — số tiền theo
     * thuế suất gộp vào {@code thttltsuat} (C53, hết gạch, JSON như HD1 v3/HD3 — đảo SI-48 cho HD5); {@code lddctthe}
     * (C72, String, N) mới — nguồn như HD1/HD3 (SI-53).
     */
    public static final CsvLayout HD5_SALES = new CsvLayout("HD5", build("HD5", 42, new Object[][]{
            {"nbmst", STRING, true}, {"khmshdon", BYTE, true}, {"khhdon", STRING, true}, {"shdon", INTEGER, true},
            {"tdlap", DATE, true}, {"nky", DATETIME, false}, {"tthai", BYTE, true}, {"nmten", STRING, false},
            {"nmmst", STRING, false}, {"tgtcthue", DECIMAL, true}, {"tgtthue", DECIMAL, true},
            {"thttltsuat", JSON, false},
            {"tgtttbso", DECIMAL, true},
            {STRUCK_PCT_ROWS}, // C55–C67: 13 dòng pct*_sotien/sothue gạch (gộp vào thttltsuat) — không phải cột CSV
            {"bhphap", BYTE, false}, {"bhpldo", STRING, false},
            {"bhpngay", DATETIME, false}, {"ttmstnmua", STRING, true}, {"lddctthe", STRING, false}}));

    /**
     * HD8 kết quả theo `API HDDT - TTHC - TMS_v3_20261106.xlsx` (SI-65): HD8!C37–C71, 21 cột, không header. Cột không hậu tố =
     * giá trị trên hệ thống (Doris), cột {@code *_nnt} = giá trị NNT gửi trong file đầu vào. Dòng gạch ngang ({@code nky} C43,
     * 13 dòng pct* C54–C66) vẫn khai báo (người dùng 08/11: giữ trong code, chỉ không đẩy ra) ⇒ {@link CsvLayout#declared()}.
     */
    public static final CsvLayout HD8_RESULT = new CsvLayout("HD8", build("HD8", 37, new Object[][]{
            {"nbmst", STRING, true}, {"khmshdon", BYTE, true}, {"khhdon", STRING, true}, {"shdon", INTEGER, true},
            {"tdlap", DATE, true}, {"tdlap_nnt", DATE, false},
            {"nky", DATETIME, false, STRUCK},
            {"tthai", BYTE, false}, {"tthai_nnt", BYTE, false},
            {"nbten", STRING, false}, {"nbten_nnt", STRING, false},
            {"tgtcthue", DECIMAL, false}, {"tgtcthue_nnt", DECIMAL, false},
            {"tgtthue", DECIMAL, false}, {"tgtthue_nnt", DECIMAL, false},
            {"thttltsuat", JSON, false}, {"thttltsuat_nnt", JSON, false},
            {"pctkct_sotien", DECIMAL, false, STRUCK}, {"pctkkknt_sotien", DECIMAL, false, STRUCK},
            {"pctcttc_sotien", DECIMAL, false, STRUCK}, {"pctktpvdc_sotien", DECIMAL, false, STRUCK},
            {"pct0_sotien", DECIMAL, false, STRUCK}, {"pct5_sotien", DECIMAL, false, STRUCK},
            {"pct5_sothue", DECIMAL, false, STRUCK}, {"pct8_sotien", DECIMAL, false, STRUCK},
            {"pct8_sothue", DECIMAL, false, STRUCK}, {"pct10_sotien", DECIMAL, false, STRUCK},
            {"pct10_sothue", DECIMAL, false, STRUCK}, {"pctkhac_sotien", DECIMAL, false, STRUCK},
            {"pctkhac_sothue", DECIMAL, false, STRUCK},
            {"bhphap", BYTE, false}, {"bhpldo", STRING, false},
            {"bhpngay", DATETIME, false}, {"ttmstnban", STRING, true}, {"lddctthe", STRING, false}}));

    /**
     * Đầu vào HD8 — SI-12 (chốt 31/10: đúng cấu trúc file kết quả HD1) + SI-65 (08/11: theo HD1 v3 15 cột, {@link #HD1_PURCHASE}).
     * Khi đọc chỉ bắt buộc khóa + tdlap: các cột còn lại có thể rỗng trong chính file HD1 đã xuất (vd ttmstnban khi MST không có
     * trong NNT_HIS); giá trị được ghi lại vào các cột {@code *_nnt} của file kết quả.
     */
    public static final CsvLayout HD8_INPUT = new CsvLayout("HD8_INPUT", HD1_PURCHASE.columns().stream()
            .map(c -> new CsvColumn(c.index(), c.name(), c.kind(), c.index() <= 5, c.specCell())).collect(Jdk8Compat.toList()));

    /** TMS!C34–C46: 13 cột đầu vào v3, không header (SI-66). */
    public static final CsvLayout TMS_REFUND_INPUT = new CsvLayout("TMS", build("TMS", 34, new Object[][]{
            {"nbmst", STRING, true}, {"khmshdon", BYTE, true}, {"khhdon", STRING, true}, {"shdon", INTEGER, true},
            {"tdlap", DATE, true}, {"nky", DATETIME, false}, {"tthai", BYTE, true}, {"nbten", STRING, true},
            {"tgtcthue", DECIMAL, true}, {"tgtthue", DECIMAL, true}, {"thttltsuat", JSON, false},
            {"ttmstnban", STRING, true}, {"lddctthe", STRING, false}}));

    private CsvLayouts() {
    }

    /**
     * Phần tử 1 giá trị {@code {n}} = n dòng đặc tả đã gạch bỏ: bỏ qua n ô nguồn, không tạo cột. Phần tử 4 giá trị
     * {@code {tên, kiểu, bắt buộc, STRUCK}} = dòng gạch ngang nhưng vẫn khai báo: cột {@link CsvColumn#struck()} (index 0, giữ ô
     * nguồn), không chiếm thứ tự cột của file.
     */
    private static List<CsvColumn> build(String sheet, int firstRow, Object[][] defs) {
        List<CsvColumn> out = new ArrayList<>(defs.length);
        int row = firstRow;
        int index = 0;
        for (Object[] d : defs) {
            if (d.length == 1) {
                row += (Integer) d[0];
                continue;
            }
            boolean struck = d.length == 4 && STRUCK.equals(d[3]);
            out.add(new CsvColumn(struck ? 0 : ++index, (String) d[0], (ValueKind) d[1], (Boolean) d[2], sheet + "!C" + row,
                    struck));
            row++;
        }
        return Jdk8Compat.listCopyOf(out);
    }
}
