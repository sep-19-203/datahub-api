package vn.teca.hddt.dataapi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.csv.CsvColumn;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.csv.ValueKind;

/** Khóa cứng thứ tự cột CSV theo workbook — đổi thứ tự = phá hợp đồng. */
class CsvLayoutsTest {

    /** HD1 v3 (`API HDDT - TTHC - TMS_v3_20261106.xlsx`, SI-52): bỏ nky (gạch), có thttltsuat, thêm lddctthe — 15 cột. */
    static final List<String> HD1 = Jdk8Compat.listOf("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "tthai", "nbten",
            "tgtcthue", "tgtthue", "thttltsuat", "bhphap", "bhpldo", "bhpngay", "ttmstnban", "lddctthe");

    /** HD8 kết quả v3 (SI-65, HD8!C37–C71): giá trị hệ thống + *_nnt; nky (C43), 13 pct* (C54–C66) gạch ⇒ không xuất. */
    static final List<String> HD8 = Jdk8Compat.listOf("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "tdlap_nnt", "tthai",
            "tthai_nnt", "nbten", "nbten_nnt", "tgtcthue", "tgtcthue_nnt", "tgtthue", "tgtthue_nnt", "thttltsuat", "thttltsuat_nnt",
            "bhphap", "bhpldo", "bhpngay", "ttmstnban", "lddctthe");

    static final List<String> HD8_STRUCK = Jdk8Compat.listOf("nky", "pctkct_sotien", "pctkkknt_sotien", "pctcttc_sotien",
            "pctktpvdc_sotien", "pct0_sotien", "pct5_sotien", "pct5_sothue", "pct8_sotien", "pct8_sothue", "pct10_sotien",
            "pct10_sothue", "pctkhac_sotien", "pctkhac_sothue");

    static final List<String> HD5 = Jdk8Compat.listOf("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "nky", "tthai", "nmten",
            "nmmst", "tgtcthue", "tgtthue", "thttltsuat", "tgtttbso", "bhphap", "bhpldo", "bhpngay", "ttmstnmua", "lddctthe");

    static final List<String> TMS = Jdk8Compat.listOf("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "nky", "tthai", "nbten",
            "tgtcthue", "tgtthue", "thttltsuat", "ttmstnban", "lddctthe");

    @Test
    void hd1_v3_has15ColumnsInSpecOrder_withSourceCells_skippingStruckRows() {
        assertThat(CsvLayouts.HD1_PURCHASE.names()).containsExactlyElementsOf(HD1);
        assertThat(CsvLayouts.HD1_PURCHASE.columns().stream().map(c -> c.specCell()).collect(Jdk8Compat.toList()))
                .containsExactly("HD1!C42", "HD1!C43", "HD1!C44", "HD1!C45", "HD1!C46", // C47 nky gạch
                        "HD1!C48", "HD1!C49", "HD1!C50", "HD1!C51", "HD1!C52", // C53–C65 pct* gạch
                        "HD1!C66", "HD1!C67", "HD1!C68", "HD1!C69", "HD1!C70");
        assertThat(CsvLayouts.HD1_PURCHASE.columns().stream().map(CsvColumn::index).collect(Jdk8Compat.toList()))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        assertThat(CsvLayouts.HD1_PURCHASE.column("ttmstnban").required()).isTrue();
        assertThat(CsvLayouts.HD1_PURCHASE.column("thttltsuat").kind()).isEqualTo(ValueKind.JSON);
        assertThat(CsvLayouts.HD1_PURCHASE.column("thttltsuat").required()).isFalse();
        assertThat(CsvLayouts.HD1_PURCHASE.column("lddctthe").required()).isFalse();
        assertThat(CsvLayouts.HD1_PURCHASE.names()).doesNotContain("nky");
    }

    @Test
    void hd5_has18ColumnsInSpecOrder() {
        // sheet 08/11 09:36 (SI-63): C53 thttltsuat hết gạch (JSON), 13 dòng pct* gạch (gộp vào thttltsuat), thêm C72 lddctthe
        assertThat(CsvLayouts.HD5_SALES.names()).containsExactlyElementsOf(HD5);
        assertThat(CsvLayouts.HD5_SALES.column("tgtttbso").specCell()).isEqualTo("HD5!C54");
        assertThat(CsvLayouts.HD5_SALES.column("bhphap").specCell()).isEqualTo("HD5!C68");
        assertThat(CsvLayouts.HD5_SALES.column("ttmstnmua").specCell()).isEqualTo("HD5!C71");
        assertThat(CsvLayouts.HD5_SALES.column("thttltsuat").specCell()).isEqualTo("HD5!C53");
        assertThat(CsvLayouts.HD5_SALES.column("lddctthe").kind()).isEqualTo(vn.teca.hddt.dataapi.domain.csv.ValueKind.STRING);
        assertThat(CsvLayouts.HD5_SALES.column("lddctthe").required()).isFalse();
        assertThat(CsvLayouts.HD5_SALES.column("tgtttbso").required()).isTrue();
        assertThat(CsvLayouts.HD5_SALES.column("nmmst").required()).isFalse();
    }

    @Test
    void hd8Result_v3_has21Columns_systemAndNntSideBySide_withSourceCells() {
        assertThat(CsvLayouts.HD8_RESULT.names()).containsExactlyElementsOf(HD8);
        assertThat(CsvLayouts.HD8_RESULT.columns().stream().map(c -> c.specCell()).collect(Jdk8Compat.toList()))
                .containsExactly("HD8!C37", "HD8!C38", "HD8!C39", "HD8!C40", "HD8!C41", "HD8!C42", // C43 nky gạch
                        "HD8!C44", "HD8!C45", "HD8!C46", "HD8!C47", "HD8!C48", "HD8!C49", "HD8!C50", "HD8!C51", "HD8!C52",
                        "HD8!C53", // C54–C66 pct* gạch
                        "HD8!C67", "HD8!C68", "HD8!C69", "HD8!C70", "HD8!C71");
        assertThat(CsvLayouts.HD8_RESULT.columns().stream().map(CsvColumn::index).collect(Jdk8Compat.toList()))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
        assertThat(CsvLayouts.HD8_RESULT.columns().stream().filter(CsvColumn::required).map(CsvColumn::name))
                .containsExactly("nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "ttmstnban");
        assertThat(CsvLayouts.HD8_RESULT.column("thttltsuat").kind()).isEqualTo(ValueKind.JSON);
        assertThat(CsvLayouts.HD8_RESULT.column("thttltsuat_nnt").kind()).isEqualTo(ValueKind.JSON);
        assertThat(CsvLayouts.HD8_RESULT.column("tgtcthue_nnt").kind()).isEqualTo(ValueKind.DECIMAL);
    }

    @Test
    void hd8Result_struckRows_stayDeclaredButAreNeverColumns() {
        // Người dùng 08/11: dòng gạch ngang giữ trong mã (để bật lại khi khách hàng yêu cầu), chỉ không đẩy ra khi gọi API.
        List<CsvColumn> struck = CsvLayouts.HD8_RESULT.declared().stream().filter(CsvColumn::struck).collect(Jdk8Compat.toList());
        assertThat(struck).extracting(CsvColumn::name).containsExactlyElementsOf(HD8_STRUCK);
        assertThat(struck).extracting(CsvColumn::specCell).containsExactly("HD8!C43", "HD8!C54", "HD8!C55", "HD8!C56",
                "HD8!C57", "HD8!C58", "HD8!C59", "HD8!C60", "HD8!C61", "HD8!C62", "HD8!C63", "HD8!C64", "HD8!C65", "HD8!C66");
        assertThat(struck).allSatisfy(c -> assertThat(c.index()).isZero());
        assertThat(CsvLayouts.HD8_RESULT.declared()).hasSize(HD8.size() + HD8_STRUCK.size());
        assertThat(CsvLayouts.HD8_RESULT.names()).doesNotContainAnyElementsOf(HD8_STRUCK);
    }

    @Test
    void hd8Input_isHd1V3_withOnlyKeyAndTdlapRequired() {
        assertThat(CsvLayouts.HD8_INPUT.names()).containsExactlyElementsOf(HD1);
        assertThat(CsvLayouts.HD8_INPUT.columns().stream().filter(CsvColumn::required).map(CsvColumn::name))
                .containsExactly("nbmst", "khmshdon", "khhdon", "shdon", "tdlap");
    }

    @Test
    void tmsInput_v3_has13Columns() {
        assertThat(CsvLayouts.TMS_REFUND_INPUT.names()).containsExactlyElementsOf(TMS);
        assertThat(CsvLayouts.TMS_REFUND_INPUT.columns().get(10).specCell()).isEqualTo("TMS!C44");
        assertThat(CsvLayouts.TMS_REFUND_INPUT.column("lddctthe").specCell()).isEqualTo("TMS!C46");
        assertThat(CsvLayouts.TMS_REFUND_INPUT.column("lddctthe").required()).isFalse();
        assertThat(CsvLayouts.TMS_REFUND_INPUT.columns().stream().map(CsvColumn::index).collect(Jdk8Compat.toList()))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
    }
}
