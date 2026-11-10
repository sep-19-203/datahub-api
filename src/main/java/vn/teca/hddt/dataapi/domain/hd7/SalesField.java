package vn.teca.hddt.dataapi.domain.hd7;

/** Trường `data.bra` của HD7 (HD7!C33–C41), kèm chỉ tiêu tờ khai và loại số liệu (giá trị / tiền thuế). */
public enum SalesField {
    TGTBR0("tgtbr0", "29", false), TGTBR5("tgtbr5", "30", false), TGTTBR5("tgttbr5", "31", true),
    TGTBR10("tgtbr10", "32", false), TGTTBR10("tgttbr10", "33", true), TGTBRKCT("tgtbrkct", "26", false),
    TGTBRKKKNT("tgtbrkkknt", "32a", false), TGTBRKTGTT("tgtbrktgtt", "32b", false),
    TGTBRKTPVDC("tgtbrktpvdc", "34a", false);

    private final String jsonName;
    private final String declarationItem;
    private final boolean tax;

    SalesField(String jsonName, String declarationItem, boolean tax) {
        this.jsonName = jsonName;
        this.declarationItem = declarationItem;
        this.tax = tax;
    }

    public String jsonName() {
        return jsonName;
    }

    public String declarationItem() {
        return declarationItem;
    }

    /** true ⇒ cộng tiền thuế (`tthue`) của các mã thuế suất được gán; false ⇒ cộng thành tiền (`thtien`). */
    public boolean tax() {
        return tax;
    }

    public static SalesField fromJsonName(String name) {
        for (SalesField f : values()) {
            if (f.jsonName.equals(name)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Trường HD7 không tồn tại: " + name);
    }
}
