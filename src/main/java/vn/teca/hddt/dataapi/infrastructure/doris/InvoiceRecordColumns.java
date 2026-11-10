package vn.teca.hddt.dataapi.infrastructure.doris;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.jdbc.core.RowMapper;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Danh sách cột HOADON_HDR (alias h) cho {@link InvoiceRecord} (không còn cột số tiền theo thuế suất PCT* — 06/11).
 * HD8 v3 (SI-65) cần thêm {@code THTTLTSUAT} (JSON nguyên văn, như HD1 v3 {@code CsvLineSql}) và {@code GCHDGOC} (→ lddctthe,
 * SI-53) — hai cột chỉ có ở HOADON_HDR.
 */
final class InvoiceRecordColumns {
    /** Cột chỉ có ở HOADON_HDR, HD_MST_LIST v5 chưa có (KI-API-32). */
    static final String HDR_ONLY = "CAST(h.THTTLTSUAT AS STRING) AS THTTLTSUAT_JSON, h.GCHDGOC";
    static final String SELECT = "h.NBMST, h.KHMSHDON, h.KHHDON, h.SHDON, h.TDLAP, h.NKY, h.TTHAI, h.NBTEN,"
            + " h.TGTCTHUE, h.TGTTHUE, h.BHPHAP, h.BHPLDO, h.BHPNGAY, h.LOAI_HD, h.ID, " + HDR_ONLY;
    /**
     * HD_MST_LIST v5 có đủ cột cho bản ghi HD8 không. {@code false} (08/11, KI-API-32: LIST v5 thiếu THTTLTSUAT/GCHDGOC) ⇒
     * HD8 luôn đọc HOADON_HDR kể cả khi bật {@code hddt.doris.list-serving} (như HD1 v3). Chỉ đổi thành true khi LIST v5 có hai
     * cột đó và {@link #LIST_SELECT} đã bổ sung chúng.
     */
    static final boolean LIST_COVERS_RECORD = false;
    /** Cùng cột từ HD_MST_LIST DDL v5 (alias l, DB-P1-03) — chiều MUA_VAO nên tên đối tác = NBTEN; thiếu {@link #HDR_ONLY}. */
    static final String LIST_SELECT = "l.NBMST, l.KHMSHDON, l.KHHDON, l.SHDON, l.TDLAP, l.NKY, l.TTHAI, l.TEN_DOI_TAC AS NBTEN,"
            + " l.TGTCTHUE, l.TGTTHUE, l.BHPHAP, l.BHPLDO, l.BHPNGAY, l.LOAI_HD, l.ID";

    static final RowMapper<InvoiceRecord> MAPPER = InvoiceRecordColumns::map;

    private InvoiceRecordColumns() {
    }

    static InvoiceRecord map(ResultSet rs, int i) throws SQLException {
        return new InvoiceRecord(rs.getString("NBMST"), DorisInvoiceQueryAdapter.intOrNull(rs, "KHMSHDON"),
                rs.getString("KHHDON"), DorisInvoiceQueryAdapter.longOrNull(rs, "SHDON"),
                rs.getObject("TDLAP", LocalDate.class), DorisInvoiceQueryAdapter.dateTime(rs, "NKY"),
                DorisInvoiceQueryAdapter.intOrNull(rs, "TTHAI"), rs.getString("NBTEN"),
                rs.getBigDecimal("TGTCTHUE"), rs.getBigDecimal("TGTTHUE"),
                DorisInvoiceQueryAdapter.intOrNull(rs, "BHPHAP"), rs.getString("BHPLDO"),
                DorisInvoiceQueryAdapter.dateTime(rs, "BHPNGAY"), null, rs.getString("LOAI_HD"), rs.getString("ID"),
                json(rs.getString("THTTLTSUAT_JSON")), rs.getString("GCHDGOC"));
    }

    /** VARIANT rỗng CAST sang chuỗi ra chữ {@code null} ⇒ coi là không có (như HD1 v3 {@code NULLIF(..., 'null')}). */
    static String json(String v) {
        return v == null || "null".equals(v) ? null : v;
    }
}
