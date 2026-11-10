package vn.teca.hddt.dataapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.application.detail.InvoiceDetailService;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.port.InvoiceQueryPort;
import vn.teca.hddt.dataapi.application.port.SummaryQueryPort;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.application.suggest.DeclarationSuggestionService;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.BusinessRuleNotConfirmedException;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.hd7.SalesField;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy;
import vn.teca.hddt.dataapi.domain.invoice.GoodsLine;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceDetail;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.invoice.TaxRateLine;
import vn.teca.hddt.dataapi.support.Fixtures;
import vn.teca.hddt.dataapi.support.TestProps;

class SyncServicesTest {
    final InvoiceQueryPort invoices = mock(InvoiceQueryPort.class);
    final TaxpayerStatusPort statuses = mock(TaxpayerStatusPort.class);

    static InvoiceHeader header(String loai) {
        return new InvoiceHeader(LocalDate.of(2026, 10, 28), "0100", loai, "id-" + loai, Fixtures.SELLER, 1, "C26MAA",
                99L, null, 1, "CTY A", new BigDecimal("940000.000000"), new BigDecimal("75200.000000"),
                Jdk8Compat.listOf(new TaxRateLine("8%", new BigDecimal("940000"), new BigDecimal("75200"))), null, null, null,
                null);
    }

    @Test
    void hd3_found_returnsHeaderItemsAndSellerStatus_inSingleDetailQuery() {
        when(statuses.configured()).thenReturn(true);
        when(invoices.findHeaders(anyString(), anyInt(), any(), anyLong(), any(), anyInt())).thenReturn(Jdk8Compat.listOf(header("INV")));
        when(invoices.findGoodsLines(any())).thenReturn(Jdk8Compat.listOf(new GoodsLine(1, "Hàng A", new BigDecimal("940000"), "8%",
                new BigDecimal("75200"))));
        when(statuses.findStatus(Fixtures.SELLER)).thenReturn(Optional.of("00"));
        InvoiceDetail d = new InvoiceDetailService(invoices, statuses)
                .get(Fixtures.SELLER, 1, "C26MAA", 99, LocalDate.of(2026, 10, 28));
        assertThat(d.ttmstnban()).isEqualTo("00");
        assertThat(d.items()).hasSize(1);
        assertThat(d.header().thttltsuat().get(0).tsuat()).isEqualTo("8%");
        verify(invoices, times(1)).findGoodsLines(any());
    }

    @Test
    void hd3_notFound_and_ambiguousInvAndMtt_neverPickFirst() {
        when(statuses.configured()).thenReturn(true);
        InvoiceDetailService s = new InvoiceDetailService(invoices, statuses);
        when(invoices.findHeaders(anyString(), anyInt(), any(), anyLong(), any(), anyInt())).thenReturn(Jdk8Compat.listOf());
        assertThatThrownBy(() -> s.get(Fixtures.SELLER, 1, "C26MAA", 99, LocalDate.of(2026, 10, 28)))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.INVOICE_NOT_FOUND);
        when(invoices.findHeaders(anyString(), anyInt(), any(), anyLong(), any(), anyInt()))
                .thenReturn(Jdk8Compat.listOf(header("INV"), header("MTT")));
        assertThatThrownBy(() -> s.get(Fixtures.SELLER, 1, "C26MAA", 99, LocalDate.of(2026, 10, 28)))
                .extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.AMBIGUOUS_INVOICE);
        verify(invoices, never()).findGoodsLines(any());
    }

    @Test
    void hd3_searchesBothDStrokeVariants() {
        when(statuses.configured()).thenReturn(true);
        when(invoices.findHeaders(anyString(), anyInt(), any(), anyLong(), any(), anyInt())).thenReturn(Jdk8Compat.listOf());
        try {
            new InvoiceDetailService(invoices, statuses).get(Fixtures.SELLER, 1, "K23TĐA", 1, LocalDate.of(2026, 1, 1));
        } catch (ApiException ignored) {
            // không tìm thấy
        }
        verify(invoices).findHeaders(eq(Fixtures.SELLER), eq(1), eq(Jdk8Compat.setOf("K23TĐA", "K23TÐA")), eq(1L), any(), anyInt());
    }

    @Test
    void hd3_withoutMstStatusSource_is503_notNull() {
        when(statuses.configured()).thenReturn(false);
        assertThatThrownBy(() -> new InvoiceDetailService(invoices, statuses).get(Fixtures.SELLER, 1, "C26MAA", 99,
                LocalDate.of(2026, 10, 28))).extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MST_STATUS_UNAVAILABLE);
    }

    @Test
    void hd7_purchasesOnly0510_salesMappedByRate_8PercentNotMapped_andNullForNoSource() {
        SummaryQueryPort summary = mock(SummaryQueryPort.class);
        MonthRange r = MonthRange.parse("09/2026", "09/2026", 12);
        // SI-55: [23]/[24] chỉ 0%, 5%, 10% — kct, 8%, khác bỏ qua; slmvao = số HĐ trong phạm vi
        when(summary.breakdown(Direction.MUA_VAO, Fixtures.BUYER, r)).thenReturn(RateBreakdown.builder()
                .add("10%", new BigDecimal("1000.5"), new BigDecimal("100.05"))
                .add("5%", new BigDecimal("20"), new BigDecimal("1"))
                .add("0%", new BigDecimal("7"), BigDecimal.ZERO)
                .add("kct", new BigDecimal("300"), null)
                .add("8%", new BigDecimal("400"), new BigDecimal("32"))
                .add(null, new BigDecimal("50"), new BigDecimal("5"))
                .invoiceCount(3).build());
        when(summary.breakdown(Direction.BAN_RA, Fixtures.BUYER, r)).thenReturn(RateBreakdown.builder()
                .add("10%", new BigDecimal("500"), new BigDecimal("50"))
                .add("8%", new BigDecimal("100"), new BigDecimal("8"))
                .add("KHAC:3.5%", new BigDecimal("999"), new BigDecimal("35")).build());
        DeclarationSuggestionService.Suggestion s = new DeclarationSuggestionService(summary, TestProps.defaults()).suggest(Fixtures.BUYER, r);
        assertThat(s.purchases().invoiceCount()).isEqualTo(3);
        assertThat(s.purchases().pretax()).isEqualByComparingTo("1027.5");
        assertThat(s.purchases().tax()).isEqualByComparingTo("101.05");
        // SI-56: 8% không ánh xạ vào [32]/[33]
        assertThat(s.sales().get(SalesField.TGTBR10)).isEqualByComparingTo("500");
        assertThat(s.sales().get(SalesField.TGTTBR10)).isEqualByComparingTo("50");
        assertThat(s.sales().get(SalesField.TGTBR0)).isEqualByComparingTo("0");
        assertThat(s.sales().get(SalesField.TGTBRKTGTT)).isNull();
        // SI-58: [34a] = KTPVDC (v3.8 Bảng 6) ⇒ có ánh xạ, không có HĐ ⇒ 0 (không phải null)
        assertThat(s.sales().get(SalesField.TGTBRKTPVDC)).isEqualByComparingTo("0");
        assertThat(s.sales()).hasSize(9);
    }

    @Test
    void hd7_failPolicy_blocksWhenKhacHasValue() {
        HddtProperties base = TestProps.defaults();
        HddtProperties props = new HddtProperties(base.api(), base.doris(), base.dse(), base.s3(), base.mstStatus(), base.hd1(),
                base.hd5(), TestProps.hd7(TestProps.fullMapping(), SalesMappingPolicy.UnassignedCodePolicy.FAIL_IF_NONZERO),
                base.hd8(), base.tms(), base.export(), base.worker(), base.security(), base.jobCodes());
        SummaryQueryPort summary = mock(SummaryQueryPort.class);
        MonthRange r = MonthRange.parse("09/2026", "09/2026", 12);
        when(summary.breakdown(Direction.MUA_VAO, Fixtures.BUYER, r)).thenReturn(RateBreakdown.builder().build());
        when(summary.breakdown(Direction.BAN_RA, Fixtures.BUYER, r)).thenReturn(RateBreakdown.builder()
                .add("KHAC:3.5%", BigDecimal.ONE, null).build());
        assertThatThrownBy(() -> new DeclarationSuggestionService(summary, props).suggest(Fixtures.BUYER, r))
                .isInstanceOf(BusinessRuleNotConfirmedException.class);
    }
}
