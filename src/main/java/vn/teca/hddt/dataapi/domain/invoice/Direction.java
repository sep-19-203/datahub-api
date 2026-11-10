package vn.teca.hddt.dataapi.domain.invoice;

/** Chiều khai thác — giá trị cột CHIEU của HD_MST_LIST / HD_MST_SUMMARY. */
public enum Direction {
    /** MST = NMMST; KY = tháng(NKY → NCMA → TDLAP). */
    MUA_VAO,
    /** MST = NBMST; KY = tháng(TDLAP). */
    BAN_RA
}
