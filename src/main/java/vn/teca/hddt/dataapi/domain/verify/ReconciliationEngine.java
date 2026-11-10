package vn.teca.hddt.dataapi.domain.verify;

import java.util.List;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Đối soát một dòng đầu vào với các hóa đơn Doris có cùng khóa (mẫu số, ký hiệu, số hóa đơn, MST người bán) trong khoảng
 * tdlap. Không bao giờ coi hai hóa đơn giống nhau chỉ vì tổng tiền bằng nhau: khóa phải khớp, sau đó so các trường theo
 * policy (chốt 01/11: tgtcthue, tgtthue, khớp tuyệt đối).
 */
public final class ReconciliationEngine {
    private final ComparisonPolicy policy;

    public ReconciliationEngine(ComparisonPolicy policy) {
        this.policy = policy;
    }

    public Verdict verify(InvoiceRecord input, List<InvoiceRecord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Verdict(Outcome.NOT_FOUND, Jdk8Compat.listOf(), null);
        }
        if (candidates.size() > 1) {
            return new Verdict(Outcome.AMBIGUOUS, Jdk8Compat.listOf(), null);
        }
        InvoiceRecord actual = candidates.get(0);
        List<CompareField> diff = policy.differences(input, actual);
        return new Verdict(diff.isEmpty() ? Outcome.MATCH : Outcome.MISMATCH, diff, actual);
    }
}
