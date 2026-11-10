package vn.teca.hddt.dataapi.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.teca.hddt.dataapi.adapter.web.RequestContext;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.support.TestProps;

/**
 * TD-API-40 (09/11) — bỏ X-Client-Id/X-Client-Secret, chỉ gọi nội bộ: bên gọi theo x-partner (TTHC → tthc-hthue,
 * TMS → tms-hthue). Có khóa ESB ⇒ x-partner + x-hmac đúng bắt buộc (y HmacFilter của tthc-sharing), thiếu X-Request-Id ⇒
 * tự sinh. Không khóa ⇒ không xác thực; không gửi x-partner ⇒ default-client (trống ⇒ 401).
 */
class EsbHmacFilterTest {
    static final String KEY = "khoa-esb-gia-cho-test";
    final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    static HddtProperties with(HddtProperties.Security security) {
        HddtProperties p = TestProps.defaults();
        return new HddtProperties(p.api(), p.doris(), p.dse(), p.s3(), p.mstStatus(), p.hd1(), p.hd5(), p.hd7(), p.hd8(),
                p.tms(), p.export(), p.worker(), security, p.jobCodes());
    }

    static final HddtProperties ESB_ON = with(TestProps.security(EsbHmacVerifierTest.esb(KEY), "noi-bo"));
    static final HddtProperties OPEN = with(TestProps.security(null, "noi-bo"));
    static final HddtProperties OPEN_NO_DEFAULT = with(TestProps.security(null, null));

    /** Request thử; header nào {@code null} thì không gửi. */
    static final class Call {
        String method = "GET";
        String path = "/hthue/v1/job/" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String partner;
        String hmac;

        Call partner(String v) { partner = v; return this; }
        Call hmac(String v) { hmac = v; return this; }
        Call requestId(String v) { requestId = v; return this; }
        Call post(String p) { method = "POST"; path = p; return this; }
    }

    final AtomicReference<ClientPrincipal> principal = new AtomicReference<>();
    final AtomicReference<String> traceId = new AtomicReference<>();

    int run(HddtProperties props, Call c) throws Exception {
        principal.set(null);
        traceId.set(null);
        ClientAuthenticationFilter f = new ClientAuthenticationFilter(new ClientRegistry(props), new RateLimiter(),
                new ObjectMapper(), props, metrics);
        MockHttpServletRequest req = new MockHttpServletRequest(c.method, c.path);
        if (c.requestId != null) {
            req.addHeader(RequestContext.HEADER_REQUEST_ID, c.requestId);
        }
        // X-Client-Id/X-Client-Secret luôn gửi kèm để chứng minh server bỏ qua
        req.addHeader("X-Client-Id", "tms-hthue");
        req.addHeader("X-Client-Secret", "bat-ky");
        if (c.partner != null) {
            req.addHeader("x-partner", c.partner);
        }
        if (c.hmac != null) {
            req.addHeader("x-hmac", c.hmac);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, (rq, rs) -> {
            principal.set(RequestContext.principal((HttpServletRequest) rq));
            traceId.set(RequestContext.traceId((HttpServletRequest) rq));
        });
        return res.getStatus();
    }

    static String todaysSignature(String partner) {
        return new EsbHmacVerifier(EsbHmacVerifierTest.esb(KEY)).sign(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), partner);
    }

    @Test
    void esbOn_onlyPartnerAndHmac_likeTthcSharing_passes() throws Exception {
        // trục gọi tthc-sharing CHỈ gắn x-partner + x-hmac — không X-Request-Id
        assertThat(run(ESB_ON, new Call().requestId(null).partner("TTHC").hmac(todaysSignature("TTHC")))).isEqualTo(200);
        assertThat(principal.get().clientId()).isEqualTo("tthc-hthue");
        assertThat(traceId.get()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void esbOn_partnerDecidesClient_caseInsensitive_xClientHeadersIgnored() throws Exception {
        String rid = UUID.randomUUID().toString();
        assertThat(run(ESB_ON, new Call().requestId(rid).partner("tms").hmac(todaysSignature("tms"))
                .post("/hthue/v1/cap-nhat-hoa-don-hoan-thue"))).isEqualTo(200);
        assertThat(principal.get().clientId()).isEqualTo("tms-hthue");
        assertThat(traceId.get()).isEqualTo(rid);
        // X-Client-Id: tms-hthue gửi kèm không nâng quyền cho TTHC
        assertThat(run(ESB_ON, new Call().partner("TTHC").hmac(todaysSignature("TTHC"))
                .post("/hthue/v1/cap-nhat-hoa-don-hoan-thue"))).isEqualTo(403);
    }

    @Test
    void esbOn_missingOrWrongSignature_is401() throws Exception {
        assertThat(run(ESB_ON, new Call())).isEqualTo(401);
        assertThat(run(ESB_ON, new Call().partner("TTHC"))).isEqualTo(401);
        assertThat(run(ESB_ON, new Call().partner("TTHC").hmac(todaysSignature("TMS")))).isEqualTo(401);
        assertThat(run(ESB_ON, new Call().requestId(null).partner("TTHC").hmac("00"))).isEqualTo(401);
        assertThat(principal.get()).isNull();
        assertThat(metrics.counter("hddt.auth.rejected", "reason", "esb-hmac").count()).isEqualTo(4.0);
    }

    @Test
    void esbOn_signedButUnknownPartner_is401_defaultClientNotUsed() throws Exception {
        assertThat(run(ESB_ON, new Call().partner("KHAC").hmac(todaysSignature("KHAC")))).isEqualTo(401);
        assertThat(metrics.counter("hddt.auth.rejected", "reason", "partner").count()).isEqualTo(1.0);
    }

    @Test
    void esbOn_malformedRequestId_is400() throws Exception {
        assertThat(run(ESB_ON, new Call().requestId("khong-phai-uuid").partner("TTHC").hmac(todaysSignature("TTHC"))))
                .isEqualTo(400);
    }

    @Test
    void open_noAuthentication_partnerStillDecidesPermissions() throws Exception {
        assertThat(run(OPEN, new Call().partner("TTHC"))).isEqualTo(200);
        assertThat(principal.get().clientId()).isEqualTo("tthc-hthue");
        assertThat(run(OPEN, new Call().partner("TTHC").post("/hthue/v1/cap-nhat-hoa-don-hoan-thue"))).isEqualTo(403);
        assertThat(run(OPEN, new Call().partner("TMS").post("/hthue/v1/cap-nhat-hoa-don-hoan-thue"))).isEqualTo(200);
        assertThat(run(OPEN, new Call().partner("KHAC"))).isEqualTo(401);
    }

    @Test
    void open_withoutPartner_usesDefaultClient_orRejectsWhenNoneConfigured() throws Exception {
        assertThat(run(OPEN, new Call().post("/hthue/v1/cap-nhat-hoa-don-hoan-thue"))).isEqualTo(200);
        assertThat(principal.get().clientId()).isEqualTo("noi-bo");
        assertThat(run(OPEN_NO_DEFAULT, new Call())).isEqualTo(401);
    }

    @Test
    void open_requestIdStillRequired() throws Exception {
        assertThat(run(OPEN, new Call().requestId(null).partner("TTHC"))).isEqualTo(400);
    }

    @Test
    void misconfiguredPartnerOrDefaultClient_failsAtStartup() {
        HddtProperties.Security base = TestProps.security(null, null);
        assertThatThrownBy(() -> new ClientRegistry(with(new HddtProperties.Security(base.clients(), null, null, null,
                Jdk8Compat.mapOf("TTHC", "khong-co"), null)))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ClientRegistry(with(new HddtProperties.Security(base.clients(), null, null, null,
                base.partnerClients(), "khong-co")))).isInstanceOf(IllegalStateException.class);
    }
}
