package vn.teca.hddt.dataapi.application.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.port.PurchaseCandidates;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;

/** DB-P1-02: khóa tra cứu, chỉ mục ứng viên và tập long của HD8. */
class CandidateIndexTest {

    @Test
    void lookupFingerprint_ignoresKhmshdon_butNotOtherKeyParts() {
        InvoiceKey a = new InvoiceKey("0100000001", 1, "C26TAA", 7);
        assertThat(new InvoiceKey("0100000001", 5, "C26TAA", 7).lookupFingerprint()).isEqualTo(a.lookupFingerprint());
        assertThat(InvoiceKey.lookupFingerprint("0100000001", "C26TAA", 7)).isEqualTo(a.lookupFingerprint());
        assertThat(new InvoiceKey("0100000001", 1, "C26TĐA", 7).lookupFingerprint()).isNotEqualTo(a.lookupFingerprint());
        assertThat(new InvoiceKey("0100000001", 1, "C26TAA", 70).lookupFingerprint()).isNotEqualTo(a.lookupFingerprint());
        assertThat(new InvoiceKey("010000000", 1, "1C26TAA", 7).lookupFingerprint()).isNotEqualTo(a.lookupFingerprint());
    }

    @Test
    void candidates_chainSameLookupKey_dedupeRefsAcrossKhmshdon_andRoundTripIds() {
        PurchaseCandidates idx = new PurchaseCandidates();
        LocalDate d = LocalDate.of(2026, 9, 15);
        String uuid = "0f8fad5b-d9cb-469f-a165-70867728950e";
        String upper = "0F8FAD5B-D9CB-469F-A165-70867728950E";
        long fp = InvoiceKey.lookupFingerprint("0100000001", "C26TAA", 3);
        idx.add(fp, d, "0101", "INV", uuid, d.withDayOfMonth(1));
        idx.add(fp, LocalDate.of(2026, 9, 15), "0101", "MTT", upper, LocalDate.of(2026, 9, 1));
        idx.add(InvoiceKey.lookupFingerprint("0100000001", "C26TAA", 4), d, "0101", "INV", "id-4", d.withDayOfMonth(1));
        assertThat(idx.size()).isEqualTo(3);
        assertThat(idx.lookup(fp)).hasSize(2);
        assertThat(idx.lookup(InvoiceKey.lookupFingerprint("0100000002", "C26TAA", 3))).isEmpty();

        Collection<PurchaseCandidates.Ref> refs = idx.refsFor(Arrays.asList(new InvoiceKey("0100000001", 1, "C26TAA", 3),
                new InvoiceKey("0100000001", 5, "C26TAA", 3), new InvoiceKey("0100000001", 1, "C26TAA", 9)));
        assertThat(refs).hasSize(2); // mẫu 1 và mẫu 5 cùng khóa tra cứu ⇒ cùng 2 ứng viên, không lặp
        assertThat(refs).extracting(PurchaseCandidates.Ref::id).containsExactlyInAnyOrder(uuid, upper); // UUID không chuẩn giữ nguyên
        assertThat(refs).extracting(PurchaseCandidates.Ref::loaiHd).containsExactlyInAnyOrder("INV", "MTT");
        PurchaseCandidates.Ref[] two = refs.toArray(new PurchaseCandidates.Ref[0]);
        assertThat(two[0].tdlap()).isSameAs(two[1].tdlap()); // ngày dùng chung một đối tượng
        assertThat(idx.lookup(InvoiceKey.lookupFingerprint("0100000001", "C26TAA", 4)).get(0).id()).isEqualTo("id-4");
    }

    @Test
    void useIndex_onlyWhenCandidatesPerInputRowWithinThreshold() {
        assertThat(PurchaseVerificationHandler.useIndex(2_071_427, 2_222, 20)).isFalse(); // DEV 07/11: 82 s vs 5,6 s
        assertThat(PurchaseVerificationHandler.useIndex(2_071_427, 1_000_000, 20)).isTrue();
        assertThat(PurchaseVerificationHandler.useIndex(0, 1, 20)).isTrue();
        assertThat(PurchaseVerificationHandler.useIndex(40, 2, 20)).isTrue();
        assertThat(PurchaseVerificationHandler.useIndex(41, 2, 20)).isFalse();
        assertThat(PurchaseVerificationHandler.useIndex(5, 5, 0)).isTrue(); // ngưỡng ≤ 0 coi như 1
    }

    @Test
    void longHashSet_matchesJavaSet_includingZeroAndGrowth() {
        LongHashSet s = new LongHashSet();
        Set<Long> ref = new HashSet<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 50_000; i++) {
            long v = i % 7 == 0 ? rnd.nextInt(100) : rnd.nextLong();
            assertThat(s.add(v)).isEqualTo(ref.add(v));
        }
        assertThat(s.add(0)).isEqualTo(ref.add(0L));
        assertThat(s.add(0)).isFalse();
        assertThat(s.size()).isEqualTo(ref.size());
        for (Long v : ref) {
            assertThat(s.contains(v)).isTrue();
        }
        for (int i = 0; i < 10_000; i++) {
            long v = rnd.nextLong();
            assertThat(s.contains(v)).isEqualTo(ref.contains(v));
        }
    }
}
