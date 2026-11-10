package vn.teca.hddt.dataapi.application.suggest;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.teca.hddt.dataapi.application.port.SummaryQueryPort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.common.TaxCode;
import vn.teca.hddt.dataapi.domain.hd7.PurchaseTotals;
import vn.teca.hddt.dataapi.domain.hd7.RateAmount;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.hd7.SalesField;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy;
import vn.teca.hddt.dataapi.domain.hd7.TaxRateCode;
import vn.teca.hddt.dataapi.domain.invoice.Direction;

/**
 * HD7 — gợi ý số liệu tờ khai (cập nhật 04/11 theo GiaiPhap v3.8). Cả mua vào và bán ra gom theo mã thuế suất trên
 * tập HĐ hợp lệ (R1): mua vào [23]/[24] chỉ các mã {@code mvao-tsuat}; bán ra qua ánh xạ {@code bra-tsuat} — trường
 * chưa có nguồn (32b, 34a) trả NULL, không trả 0; số liệu thuế suất không thuộc chỉ tiêu nào (8%, KHAC...) bỏ qua và
 * ghi cảnh báo.
 */
@Service
public class DeclarationSuggestionService {
    private static final Logger log = LoggerFactory.getLogger(DeclarationSuggestionService.class);

    /** {@code sales}: giá trị NULL = chỉ tiêu chưa có nguồn. */
    public static final class Suggestion {
        private final PurchaseTotals purchases;
        private final Map<SalesField, BigDecimal> sales;

        public Suggestion(PurchaseTotals purchases, Map<SalesField, BigDecimal> sales) {
            this.purchases = purchases;
            this.sales = sales;
        }

        public PurchaseTotals purchases() {
            return purchases;
        }

        public Map<SalesField, BigDecimal> sales() {
            return sales;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Suggestion)) {
                return false;
            }
            Suggestion other = (Suggestion) o;
            return Objects.equals(purchases, other.purchases)
                    && Objects.equals(sales, other.sales);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(purchases);
            result = 31 * result + Objects.hashCode(sales);
            return result;
        }

        @Override
        public String toString() {
            return "Suggestion[purchases=" + purchases
                    + ", sales=" + sales + "]";
        }
    }

    private final SummaryQueryPort summary;
    private final SalesMappingPolicy policy;
    private final Set<String> purchaseCodes;

    public DeclarationSuggestionService(SummaryQueryPort summary, HddtProperties props) {
        this.summary = summary;
        this.policy = policyFrom(props.hd7());
        Set<String> codes = new LinkedHashSet<>();
        for (String c : props.hd7().mvaoTsuat()) {
            codes.add(TaxRateCode.normalize(c));
        }
        this.purchaseCodes = codes;
    }

    static SalesMappingPolicy policyFrom(HddtProperties.Hd7 cfg) {
        Map<SalesField, List<String>> mapping = new EnumMap<>(SalesField.class);
        if (cfg.braTsuat() != null) {
            cfg.braTsuat().forEach((field, codes) -> mapping.put(SalesField.fromJsonName(field), codes));
        }
        return new SalesMappingPolicy(mapping, cfg.unassignedTsuatPolicy());
    }

    public Suggestion suggest(String mst, MonthRange range) {
        PurchaseTotals purchases = purchases(mst, range);
        SalesMappingPolicy.Result sales = policy.apply(summary.breakdown(Direction.BAN_RA, mst, range));
        if (!sales.unassigned().isEmpty()) {
            log.warn("HD7 MST {} kỳ {}–{}: bỏ qua số liệu bán ra không thuộc chỉ tiêu nào (thuế suất → thành tiền/thuế): {}",
                    TaxCode.mask(mst), range.firstDay(), range.lastPeriod(), sales.unassigned());
        }
        return new Suggestion(purchases, sales.values());
    }

    /**
     * [23]/[24] (04/11, GiaiPhap v3.8 Bảng 6): cộng thtien/tthue của các mã {@code hddt.hd7.mvao-tsuat}; phần thuế suất
     * khác (KCT, KKKNT, 8%, KHAC, không xác định) bỏ qua + cảnh báo. slmvao = số HĐ mua vào hợp lệ trong phạm vi (R1).
     */
    private PurchaseTotals purchases(String mst, MonthRange range) {
        RateBreakdown b = summary.breakdown(Direction.MUA_VAO, mst, range);
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        Map<String, RateAmount> ignored = new LinkedHashMap<>();
        for (Map.Entry<String, RateAmount> e : b.byRate().entrySet()) {
            if (purchaseCodes.contains(e.getKey())) {
                amount = amount.add(e.getValue().amount());
                tax = tax.add(e.getValue().tax());
            } else if (!e.getValue().isZero()) {
                ignored.put(e.getKey(), e.getValue());
            }
        }
        if (!ignored.isEmpty()) {
            log.warn("HD7 MST {} kỳ {}–{}: bỏ qua số liệu mua vào không thuộc [23]/[24] (thuế suất → thành tiền/thuế): {}",
                    TaxCode.mask(mst), range.firstDay(), range.lastPeriod(), ignored);
        }
        return new PurchaseTotals(amount, tax, b.invoiceCount());
    }
}
