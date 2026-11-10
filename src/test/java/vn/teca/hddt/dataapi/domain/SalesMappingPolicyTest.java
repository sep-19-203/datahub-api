package vn.teca.hddt.dataapi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.domain.common.BusinessRuleNotConfirmedException;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.RateAmount;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.hd7.SalesField;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy.UnassignedCodePolicy;
import vn.teca.hddt.dataapi.domain.hd7.TaxRateCode;

class SalesMappingPolicyTest {

    /** Ánh xạ chốt 01/11. */
    static Map<SalesField, List<String>> approved() {
        Map<SalesField, List<String>> m = new EnumMap<>(SalesField.class);
        m.put(SalesField.TGTBR0, Jdk8Compat.listOf("0%"));
        m.put(SalesField.TGTBR5, Jdk8Compat.listOf("5%"));
        m.put(SalesField.TGTTBR5, Jdk8Compat.listOf("5%"));
        m.put(SalesField.TGTBR10, Jdk8Compat.listOf("10%", "8%"));
        m.put(SalesField.TGTTBR10, Jdk8Compat.listOf("10%", "8%"));
        m.put(SalesField.TGTBRKCT, Jdk8Compat.listOf("KCT"));
        m.put(SalesField.TGTBRKKKNT, Jdk8Compat.listOf("KKKNT"));
        m.put(SalesField.TGTBRKTGTT, Jdk8Compat.listOf());
        m.put(SalesField.TGTBRKTPVDC, Jdk8Compat.listOf());
        return m;
    }

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void codesAreNormalized_numericFormsMerge_textKeptLowercase() {
        assertThat(TaxRateCode.normalize(" 10 % ")).isEqualTo("10%");
        assertThat(TaxRateCode.normalize("10.00%")).isEqualTo("10%");
        assertThat(TaxRateCode.normalize("8")).isEqualTo("8%");
        assertThat(TaxRateCode.normalize("0%")).isEqualTo("0%");
        assertThat(TaxRateCode.normalize("KCT")).isEqualTo("kct");
        assertThat(TaxRateCode.normalize("KHAC:3.5%")).isEqualTo("khac:3.5%");
        assertThat(TaxRateCode.normalize(null)).isEqualTo(TaxRateCode.UNKNOWN);
        assertThat(TaxRateCode.normalize("  ")).isEqualTo(TaxRateCode.UNKNOWN);
    }

    @Test
    void eightPercent_isAddedTo32And33_withRealTax() {
        RateBreakdown b = RateBreakdown.builder()
                .add("10%", d("1000"), d("100"))
                .add("8%", d("940000"), d("75200"))
                .add("10.00%", d("0.000001"), d("0")).build();
        Map<SalesField, BigDecimal> out = new SalesMappingPolicy(approved(), UnassignedCodePolicy.IGNORE).apply(b).values();
        assertThat(out.get(SalesField.TGTBR10)).isEqualByComparingTo("941000.000001");
        assertThat(out.get(SalesField.TGTTBR10)).isEqualByComparingTo("75300");
    }

    @Test
    void fieldsWithoutSource_areNull_notZero_andOthersZeroWhenNoInvoices() {
        Map<SalesField, BigDecimal> out = new SalesMappingPolicy(approved(), UnassignedCodePolicy.IGNORE)
                .apply(RateBreakdown.builder().build()).values();
        assertThat(out).hasSize(9);
        assertThat(out.get(SalesField.TGTBRKTGTT)).isNull();
        assertThat(out.get(SalesField.TGTBRKTPVDC)).isNull();
        assertThat(out.get(SalesField.TGTBR0)).isEqualByComparingTo("0");
        assertThat(out.get(SalesField.TGTBRKCT)).isEqualByComparingTo("0");
    }

    @Test
    void eachCodeGoesToItsField_amountVsTax() {
        RateBreakdown b = RateBreakdown.builder()
                .add("0%", d("10"), null).add("5%", d("200"), d("10")).add("KCT", d("30"), null)
                .add("kkknt", d("40"), d("0")).build();
        Map<SalesField, BigDecimal> out = new SalesMappingPolicy(approved(), UnassignedCodePolicy.IGNORE).apply(b).values();
        assertThat(out.get(SalesField.TGTBR0)).isEqualByComparingTo("10");
        assertThat(out.get(SalesField.TGTBR5)).isEqualByComparingTo("200");
        assertThat(out.get(SalesField.TGTTBR5)).isEqualByComparingTo("10");
        assertThat(out.get(SalesField.TGTBRKCT)).isEqualByComparingTo("30");
        assertThat(out.get(SalesField.TGTBRKKKNT)).isEqualByComparingTo("40");
        assertThat(out.get(SalesField.TGTBR10)).isEqualByComparingTo("0");
    }

    @Test
    void khacAndUnknown_areIgnoredButReported_whenPolicyIgnore() {
        RateBreakdown b = RateBreakdown.builder()
                .add("KHAC:3.5%", d("100"), d("3.5")).add(null, d("7"), null).add("10%", d("50"), d("5")).build();
        SalesMappingPolicy.Result r = new SalesMappingPolicy(approved(), UnassignedCodePolicy.IGNORE).apply(b);
        assertThat(r.values().get(SalesField.TGTBR10)).isEqualByComparingTo("50");
        assertThat(r.unassigned()).containsOnlyKeys("khac:3.5%", TaxRateCode.UNKNOWN);
        assertThat(r.unassigned().get("khac:3.5%")).isEqualTo(new RateAmount(d("100"), d("3.5")));
    }

    @Test
    void khac_blocks_whenPolicyFailIfNonzero() {
        RateBreakdown b = RateBreakdown.builder().add("KHAC:3.5%", d("100"), d("3.5")).build();
        assertThatThrownBy(() -> new SalesMappingPolicy(approved(), UnassignedCodePolicy.FAIL_IF_NONZERO).apply(b))
                .isInstanceOf(BusinessRuleNotConfirmedException.class).hasMessageContaining("khac:3.5%");
        RateBreakdown zero = RateBreakdown.builder().add("KHAC:3.5%", d("0"), null).build();
        assertThat(new SalesMappingPolicy(approved(), UnassignedCodePolicy.FAIL_IF_NONZERO).apply(zero).unassigned())
                .isEmpty();
    }

    @Test
    void sameCodeOnTwoAmountFields_isRejected_toAvoidDoubleCounting() {
        Map<SalesField, List<String>> m = approved();
        m.put(SalesField.TGTBR0, Jdk8Compat.listOf("0%", "8%"));
        assertThatThrownBy(() -> new SalesMappingPolicy(m, UnassignedCodePolicy.IGNORE)).hasMessageContaining("cộng trùng");
    }

    @Test
    void missingFieldInConfiguration_isRejected() {
        Map<SalesField, List<String>> m = approved();
        m.remove(SalesField.TGTBRKTPVDC);
        assertThatThrownBy(() -> new SalesMappingPolicy(m, UnassignedCodePolicy.IGNORE)).hasMessageContaining("tgtbrktpvdc");
    }
}
