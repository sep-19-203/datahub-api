package vn.teca.hddt.dataapi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;
import vn.teca.hddt.dataapi.domain.verify.CompareField;
import vn.teca.hddt.dataapi.domain.verify.ComparisonPolicy;
import vn.teca.hddt.dataapi.domain.verify.Outcome;
import vn.teca.hddt.dataapi.domain.verify.ReconciliationEngine;
import vn.teca.hddt.dataapi.domain.verify.Verdict;
import vn.teca.hddt.dataapi.support.Fixtures;

class ReconciliationEngineTest {
    /** Quy tắc chốt 01/11: chỉ so tgtcthue, tgtthue, khớp tuyệt đối. */
    final ComparisonPolicy exact = new ComparisonPolicy(Jdk8Compat.listOf(CompareField.TGTCTHUE, CompareField.TGTTHUE), BigDecimal.ZERO);

    @Test
    void notFound_hasNoDorisRecord() {
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        Verdict v = new ReconciliationEngine(exact).verify(in, Jdk8Compat.listOf());
        assertThat(v.outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(v.discrepancy()).isTrue();
        assertThat(v.actual()).isNull();
    }

    @Test
    void fieldsOutsideRule_doNotMakeRowInvalid() {
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        InvoiceRecord doris = new InvoiceRecord(in.nbmst(), in.khmshdon(), in.khhdon(), in.shdon(), in.tdlap().plusDays(1),
                null, 5, "TEN KHAC", in.tgtcthue(), in.tgtthue(), 1, "lý do", null, "06", "INV", "id-1");
        assertThat(new ReconciliationEngine(exact).verify(in, Jdk8Compat.listOf(doris)).outcome()).isEqualTo(Outcome.MATCH);
    }

    @Test
    void sameAmountsDoNotMakeDifferentInvoicesEqual_keysDecideCandidates() {
        // Ứng viên chỉ được truyền vào khi khóa khớp; tổng tiền bằng nhau không đủ (engine không tự tìm theo tiền).
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        assertThat(new ReconciliationEngine(exact).verify(in, null).outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void scaleDifferenceIsNotADiscrepancy_butOneDongIs_withZeroTolerance() {
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        InvoiceRecord sameValue = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000.000000");
        InvoiceRecord offByOne = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940001");
        ReconciliationEngine e = new ReconciliationEngine(exact);
        assertThat(e.verify(in, Jdk8Compat.listOf(sameValue)).outcome()).isEqualTo(Outcome.MATCH);
        Verdict v = e.verify(in, Jdk8Compat.listOf(offByOne));
        assertThat(v.outcome()).isEqualTo(Outcome.MISMATCH);
        assertThat(v.differences()).containsExactly(CompareField.TGTCTHUE);
        assertThat(v.actual().tgtcthue()).isEqualByComparingTo("940001"); // bản ghi Doris để thống kê
    }

    @Test
    void toleranceOnlyAppliesWhenConfigured() {
        ComparisonPolicy tolerant = new ComparisonPolicy(Jdk8Compat.listOf(CompareField.TGTCTHUE), new BigDecimal("1"));
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        assertThat(new ReconciliationEngine(tolerant).verify(in,
                Jdk8Compat.listOf(Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940001"))).outcome()).isEqualTo(Outcome.MATCH);
    }

    @Test
    void nullIsNotZero() {
        ComparisonPolicy p = new ComparisonPolicy(Jdk8Compat.listOf(CompareField.TGTTHUE), BigDecimal.ZERO);
        InvoiceRecord a = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "1");
        InvoiceRecord zero = new InvoiceRecord(a.nbmst(), a.khmshdon(), a.khhdon(), a.shdon(), a.tdlap(), a.nky(), a.tthai(),
                a.nbten(), a.tgtcthue(), BigDecimal.ZERO, null, null, null, null, null, null);
        InvoiceRecord missing = new InvoiceRecord(a.nbmst(), a.khmshdon(), a.khhdon(), a.shdon(), a.tdlap(), a.nky(),
                a.tthai(), a.nbten(), a.tgtcthue(), null, null, null, null, null, null, null);
        assertThat(p.differences(zero, missing)).containsExactly(CompareField.TGTTHUE);
    }

    @Test
    void multipleCandidates_areAmbiguous_notFirstPicked() {
        InvoiceRecord in = Fixtures.record(Fixtures.SELLER, "C26TAA", 1, "940000");
        Verdict v = new ReconciliationEngine(exact).verify(in, Jdk8Compat.listOf(in, in));
        assertThat(v.outcome()).isEqualTo(Outcome.AMBIGUOUS);
        assertThat(v.actual()).isNull();
    }
}
