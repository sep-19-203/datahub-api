package vn.teca.hddt.dataapi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.domain.common.DomainValidationException;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.common.TaxCode;
import vn.teca.hddt.dataapi.domain.invoice.KhhdonVariants;
import vn.teca.hddt.dataapi.domain.job.JobState;
import vn.teca.hddt.dataapi.domain.job.JobType;
import vn.teca.hddt.dataapi.domain.job.PublicJobStatus;

class DomainRulesTest {

    @Test
    void monthRange_inclusiveOfLastMonth_andPeriodBoundsMatchKyColumn() {
        MonthRange r = MonthRange.parse("01/2026", "03/2026", 12);
        assertThat(r.months()).isEqualTo(3);
        assertThat(r.firstDay()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.lastPeriod()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(r.endExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void monthRange_crossYear_andSingleMonth() {
        assertThat(MonthRange.parse("12/2025", "01/2026", 12).months()).isEqualTo(2);
        MonthRange one = MonthRange.parse("09/2026", "09/2026", 1);
        assertThat(one.from()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(one.endExclusive()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void monthRange_rejectsBadFormat_reversedRange_andTooLong() {
        assertThatThrownBy(() -> MonthRange.parse("1/2026", "02/2026", 12)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> MonthRange.parse("13/2026", "02/2027", 12)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> MonthRange.parse("2026-01", "02/2026", 12)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> MonthRange.parse("03/2026", "02/2026", 12))
                .hasMessageContaining("tthang phải nhỏ hơn hoặc bằng dthang");
        assertThatThrownBy(() -> MonthRange.parse("01/2026", "01/2027", 12)).hasMessageContaining("12 tháng");
    }

    @Test
    void taxCode_formats_andMasking() {
        assertThat(TaxCode.isValid("0100000001")).isTrue();
        assertThat(TaxCode.isValid("0100000001-001")).isTrue();
        assertThat(TaxCode.isValid("001234567890")).isTrue();
        assertThat(TaxCode.isValid("010000000")).isFalse();
        assertThat(TaxCode.isValid("0100000001' OR 1=1")).isFalse();
        assertThat(TaxCode.mask("0100000001")).isEqualTo("010****01").doesNotContain("00000");
    }

    @Test
    void khhdonVariants_coverBothDStrokeCodePoints_withoutTouchingOtherSymbols() {
        assertThat(KhhdonVariants.of("C26TAA")).containsExactly("C26TAA");
        assertThat(KhhdonVariants.of("K23TĐA")).containsExactly("K23TĐA", "K23TÐA");
        assertThat(KhhdonVariants.of("K23TÐA")).containsExactly("K23TÐA", "K23TĐA");
    }

    @Test
    void jobState_mapsToPublicEnum_retryIsStillProcessing() {
        assertThat(JobState.QUEUED.publicStatus()).isEqualTo(PublicJobStatus.TIEP_NHAN);
        assertThat(JobState.RUNNING.publicStatus()).isEqualTo(PublicJobStatus.DANG_XU_LY);
        assertThat(JobState.RETRY_WAIT.publicStatus()).isEqualTo(PublicJobStatus.DANG_XU_LY);
        assertThat(JobState.SUCCEEDED.publicStatus()).isEqualTo(PublicJobStatus.HOAN_THANH);
        assertThat(JobState.FAILED.publicStatus()).isEqualTo(PublicJobStatus.LOI);
    }

    @Test
    void jobCodes_matchSpecTables() {
        assertThat(JobType.PURCHASE_LIST.defaultCode()).isEqualTo("hthue.mua-vao");      // HD1!Q31
        assertThat(JobType.SALES_LIST.defaultCode()).isEqualTo("hthue.ban-ra");          // HD5!Q31
        assertThat(JobType.REFUND_UPDATE.defaultCode()).isEqualTo("hthue.cap-nhat");     // TMS!Q27
        assertThat(JobType.PURCHASE_VERIFY.defaultCode()).isEqualTo("hthue.mua-vao.ktra"); // HD8!Q30 v3 (SI-04, SI-65)
    }
}
