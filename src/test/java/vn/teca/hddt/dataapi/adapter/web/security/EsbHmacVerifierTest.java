package vn.teca.hddt.dataapi.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * TD-API-40: chữ ký ESB như HmacFilter của hddt-tthc-sharing — hex(HMAC-SHA256(khóa, "yyyyMMdd.partner")), ngày giờ VN.
 * Giá trị mong đợi tính ĐỘC LẬP bằng Node: crypto.createHmac('sha256', 'khoa-esb-gia-cho-test').update(...).digest('hex').
 * Khóa giả, không phải khóa thật của ESB.
 */
class EsbHmacVerifierTest {
    static final String KEY = "khoa-esb-gia-cho-test";
    static final String TTHC_20261109 = "ca117925baaf80fa9c86c99bbccd239facdc58e6a0e70461e03ca03c92fcc150";
    static final String TTHC_20261108 = "9aaffb8192619c70740bf804b0538974b10c3fdc6a9dab8347027f82b5fc2719";
    static final String TTHC_20261110 = "ce2a63b86aef5ac1941ce814ee95a14e88808b25c0f655808ca20248516a4d99";
    static final String TMS_20261109 = "7f9904581c51d99664de96952171c59c1c95f486980a08578bf530d49a61803b";

    static HddtProperties.Security.Esb esb(String key, String... allowed) {
        return new HddtProperties.Security.Esb(key, "x-partner", "x-hmac", Jdk8Compat.listOf(allowed), "Asia/Ho_Chi_Minh",
                Duration.ofMinutes(5));
    }

    /** 2026-11-09 12:00 giờ VN. */
    static final Instant NOON_VN = Instant.parse("2026-11-09T05:00:00Z");

    @Test
    void signature_matchesTthcSharingAlgorithm() {
        EsbHmacVerifier v = new EsbHmacVerifier(esb(KEY));
        assertThat(v.sign(LocalDate.of(2026, 11, 9), "TTHC")).isEqualTo(TTHC_20261109);
        assertThat(v.sign(LocalDate.of(2026, 11, 9), "TMS")).isEqualTo(TMS_20261109);
        assertThat(v.verify("TTHC", TTHC_20261109, NOON_VN)).isTrue();
        assertThat(v.verify("TTHC", TTHC_20261109.toUpperCase(), NOON_VN)).isTrue();
        assertThat(v.verify("TMS", TMS_20261109, NOON_VN)).isTrue();
    }

    @Test
    void wrongPartnerDayOrKey_isRejected() {
        EsbHmacVerifier v = new EsbHmacVerifier(esb(KEY));
        assertThat(v.verify("TMS", TTHC_20261109, NOON_VN)).isFalse();              // chữ ký của đối tác khác
        assertThat(v.verify("TTHC", TTHC_20261108, NOON_VN)).isFalse();             // chữ ký hôm qua, giữa trưa
        assertThat(v.verify("TTHC", TTHC_20261109, Instant.parse("2026-11-10T05:00:00Z"))).isFalse();
        assertThat(new EsbHmacVerifier(esb("khoa-khac")).verify("TTHC", TTHC_20261109, NOON_VN)).isFalse();
        assertThat(v.verify(null, TTHC_20261109, NOON_VN)).isFalse();
        assertThat(v.verify("TTHC", null, NOON_VN)).isFalse();
        assertThat(v.verify("TTHC", " ", NOON_VN)).isFalse();
        assertThat(v.verify("TTHC", "abc", NOON_VN)).isFalse();
    }

    @Test
    void dayIsVietnamTime_notJvmOrUtc_andClockSkewCoversMidnight() {
        EsbHmacVerifier v = new EsbHmacVerifier(esb(KEY));
        // 2026-11-08T17:30Z = 00:30 ngày 09/11 giờ VN (UTC vẫn là 08/11) ⇒ ngày ký là 09/11
        assertThat(v.verify("TTHC", TTHC_20261109, Instant.parse("2026-11-08T17:30:00Z"))).isTrue();
        // 00:03 ngày 10/11 giờ VN: ESB chậm 3 phút còn ký ngày 09/11 ⇒ vẫn nhận (lệch ≤ 5 phút), ngày 10/11 cũng nhận
        Instant justAfterMidnight = Instant.parse("2026-11-09T17:03:00Z");
        assertThat(v.verify("TTHC", TTHC_20261109, justAfterMidnight)).isTrue();
        assertThat(v.verify("TTHC", TTHC_20261110, justAfterMidnight)).isTrue();
        // 00:10 ngày 10/11: quá 5 phút ⇒ chữ ký ngày 09/11 hết hiệu lực
        assertThat(v.verify("TTHC", TTHC_20261109, Instant.parse("2026-11-09T17:10:00Z"))).isFalse();
    }

    @Test
    void allowedPartners_limitsWhoMayCall_caseInsensitive() {
        EsbHmacVerifier v = new EsbHmacVerifier(esb(KEY, "tthc"));
        assertThat(v.verify("TTHC", TTHC_20261109, NOON_VN)).isTrue();
        assertThat(v.verify("TMS", TMS_20261109, NOON_VN)).isFalse();   // chữ ký đúng nhưng không nằm trong danh sách
    }

    @Test
    void disabledWhenKeyMissing_andKeyNeverPrinted() {
        assertThat(esb("").enabled()).isFalse();
        assertThat(esb(null).enabled()).isFalse();
        assertThatThrownBy(() -> new EsbHmacVerifier(esb(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThat(esb(KEY).toString()).doesNotContain(KEY).contains("enabled=true");
    }
}
