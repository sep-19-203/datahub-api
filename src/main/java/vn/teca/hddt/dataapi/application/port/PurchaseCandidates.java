package vn.teca.hddt.dataapi.application.port;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;

/**
 * DB-P1-02 — chỉ mục ứng viên HĐ mua vào của MỘT job HD8, dựng từ một truy vấn luồng trên HD_MST_LIST (bước A) và chỉ giữ
 * dòng có khóa tra cứu nằm trong file đầu vào. Khóa = {@link InvoiceKey#lookupFingerprint} (nbmst, khhdon, shdon); giá trị =
 * khóa vật lý (TDLAP, CQT, LOAI_HD, ID) + KY của dòng LIST để bước B tra theo khóa literal.
 * Gọn bộ nhớ (~110 B/ứng viên): ngày/CQT/loại dùng chung một đối tượng, ID dạng UUID chuẩn lưu 2 long.
 * Không an toàn đa luồng (một job một luồng).
 */
public final class PurchaseCandidates {

    /** Khóa vật lý một hóa đơn (bằng nhau theo đối tượng: mỗi dòng LIST thêm một lần). */
    public static final class Ref {
        private final LocalDate tdlap;
        private final String cqt;
        private final String loaiHd;
        private final LocalDate ky;
        private final long idHi;
        private final long idLo;
        private final String idRaw;
        private Ref next;

        Ref(LocalDate tdlap, String cqt, String loaiHd, LocalDate ky, long idHi, long idLo, String idRaw) {
            this.tdlap = tdlap;
            this.cqt = cqt;
            this.loaiHd = loaiHd;
            this.ky = ky;
            this.idHi = idHi;
            this.idLo = idLo;
            this.idRaw = idRaw;
        }

        public LocalDate tdlap() {
            return tdlap;
        }

        public String cqt() {
            return cqt;
        }

        public String loaiHd() {
            return loaiHd;
        }

        /** KY của dòng HD_MST_LIST (tháng NKY→NCMA→TDLAP với MUA_VAO). */
        public LocalDate ky() {
            return ky;
        }

        public String id() {
            return idRaw != null ? idRaw : new UUID(idHi, idLo).toString();
        }

        @Override
        public String toString() {
            return "Ref[" + tdlap + "/" + cqt + "/" + loaiHd + "/" + id() + ", ky=" + ky + "]";
        }
    }

    private final Map<Long, Ref> byFingerprint = new HashMap<>();
    private final Map<Object, Object> shared = new HashMap<>();
    private long size;

    @SuppressWarnings("unchecked")
    private <T> T share(T v) {
        if (v == null) {
            return null;
        }
        Object prev = shared.putIfAbsent(v, v);
        return prev == null ? v : (T) prev;
    }

    /** Thêm một dòng LIST (ứng viên) dưới khóa tra cứu của nó. */
    public void add(long lookupFingerprint, LocalDate tdlap, String cqt, String loaiHd, String id, LocalDate ky) {
        long hi = 0;
        long lo = 0;
        String raw = id;
        try {
            UUID u = UUID.fromString(id);
            if (u.toString().equals(id)) {
                hi = u.getMostSignificantBits();
                lo = u.getLeastSignificantBits();
                raw = null;
            }
        } catch (IllegalArgumentException notUuid) {
            // giữ nguyên chuỗi
        }
        Ref r = new Ref(share(tdlap), share(cqt), share(loaiHd), share(ky), hi, lo, raw);
        r.next = byFingerprint.put(lookupFingerprint, r);
        size++;
    }

    public long size() {
        return size;
    }

    /** Mọi ứng viên của một khóa tra cứu (có thể nhiều: INV/MTT cùng ID, khác KHMSHDON, va chạm). */
    public List<Ref> lookup(long lookupFingerprint) {
        List<Ref> out = new ArrayList<>();
        for (Ref r = byFingerprint.get(lookupFingerprint); r != null; r = r.next) {
            out.add(r);
        }
        return out;
    }

    /** Khóa vật lý (không lặp) của các khóa nghiệp vụ trong lô, theo thứ tự gặp. */
    public Collection<Ref> refsFor(Collection<InvoiceKey> keys) {
        Set<Ref> out = new LinkedHashSet<>();
        for (InvoiceKey k : keys) {
            for (Ref r = byFingerprint.get(k.lookupFingerprint()); r != null; r = r.next) {
                out.add(r);
            }
        }
        return out;
    }
}
