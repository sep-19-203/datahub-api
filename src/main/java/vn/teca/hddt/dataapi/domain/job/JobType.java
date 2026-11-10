package vn.teca.hddt.dataapi.domain.job;

/** Loại job bất đồng bộ. Mã jobCode công khai mặc định theo bảng đặc tả; HD8 là đề xuất (SI-04), cấu hình được. */
public enum JobType {
    /** HD1 — HD1!Q31. */
    PURCHASE_LIST("hthue.mua-vao"),
    /** HD5 — HD5!Q31. */
    SALES_LIST("hthue.ban-ra"),
    /** HD8 — HD8!Q30 bản v3 (06/11) "hthue.mua-vao.ktra"; bản cũ ghi nhầm "hthue.ban-ra" (SI-04). */
    PURCHASE_VERIFY("hthue.mua-vao.ktra"),
    /** TMS — TMS!Q27 (SI-05). */
    REFUND_UPDATE("hthue.cap-nhat");

    private final String defaultCode;

    JobType(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String defaultCode() {
        return defaultCode;
    }
}
