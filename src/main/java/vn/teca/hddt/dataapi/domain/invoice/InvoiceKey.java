package vn.teca.hddt.dataapi.domain.invoice;

import java.util.Objects;

/** Khóa nghiệp vụ hóa đơn (PK DSE: nbmst, khmshdon, khhdon, shdon). KHHDON giữ nguyên ký tự, không chuẩn hóa. */
public final class InvoiceKey {
    private final String nbmst;
    private final int khmshdon;
    private final String khhdon;
    private final long shdon;

    public InvoiceKey(String nbmst, int khmshdon, String khhdon, long shdon) {
        Objects.requireNonNull(nbmst, "nbmst");
        Objects.requireNonNull(khhdon, "khhdon");
        this.nbmst = nbmst;
        this.khmshdon = khmshdon;
        this.khhdon = khhdon;
        this.shdon = shdon;
    }

    public String nbmst() {
        return nbmst;
    }

    public int khmshdon() {
        return khmshdon;
    }

    public String khhdon() {
        return khhdon;
    }

    public long shdon() {
        return shdon;
    }

    /** Dấu vân tay tra cứu (nbmst, khhdon, shdon) — KHÔNG gồm khmshdon (HD_MST_LIST ≤ v4 không có cột này; DB-P1-02). */
    public long lookupFingerprint() {
        return lookupFingerprint(nbmst, khhdon, shdon);
    }

    /** 64-bit, cùng công thức cho khóa đầu vào HD8 và dòng HD_MST_LIST; va chạm chỉ làm tra thừa (lọc lại bằng khóa đủ). */
    public static long lookupFingerprint(String nbmst, String khhdon, long shdon) {
        long h = 1125899906842597L;
        String s = nbmst + '\u0001' + khhdon + '\u0001' + shdon;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
            h ^= (h >>> 29);
        }
        return h;
    }

    @Override
    public String toString() {
        return "InvoiceKey[" + khmshdon + "/" + khhdon + "/" + shdon + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceKey)) {
            return false;
        }
        InvoiceKey other = (InvoiceKey) o;
        return Objects.equals(nbmst, other.nbmst)
                && khmshdon == other.khmshdon
                && Objects.equals(khhdon, other.khhdon)
                && shdon == other.shdon;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(nbmst);
        result = 31 * result + Integer.hashCode(khmshdon);
        result = 31 * result + Objects.hashCode(khhdon);
        result = 31 * result + Long.hashCode(shdon);
        return result;
    }
}
