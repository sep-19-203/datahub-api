package vn.teca.hddt.dataapi.application.verify;

/**
 * Tập long địa chỉ mở (không boxing): ~16 B/phần tử ở hệ số tải ≤ 0,5 — 1 triệu khóa tra cứu HD8 ≈ 32 MB thay vì ~64 MB
 * của HashSet&lt;Long&gt; (DB-P1-02). Không an toàn đa luồng.
 */
final class LongHashSet {
    private long[] table = new long[1 << 10];
    private int size;
    private boolean hasZero;

    private static int slot(long v, int mask) {
        long h = v * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    /** @return true nếu chưa có */
    boolean add(long v) {
        if (v == 0) {
            boolean added = !hasZero;
            hasZero = true;
            return added;
        }
        if ((size + 1) * 2 > table.length) {
            grow();
        }
        int mask = table.length - 1;
        for (int i = slot(v, mask); ; i = (i + 1) & mask) {
            if (table[i] == 0) {
                table[i] = v;
                size++;
                return true;
            }
            if (table[i] == v) {
                return false;
            }
        }
    }

    boolean contains(long v) {
        if (v == 0) {
            return hasZero;
        }
        int mask = table.length - 1;
        for (int i = slot(v, mask); ; i = (i + 1) & mask) {
            if (table[i] == 0) {
                return false;
            }
            if (table[i] == v) {
                return true;
            }
        }
    }

    int size() {
        return size + (hasZero ? 1 : 0);
    }

    private void grow() {
        long[] old = table;
        table = new long[old.length * 2];
        int mask = table.length - 1;
        for (long v : old) {
            if (v != 0) {
                int i = slot(v, mask);
                while (table[i] != 0) {
                    i = (i + 1) & mask;
                }
                table[i] = v;
            }
        }
    }
}
