package vn.teca.hddt.dataapi.adapter.web.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import vn.teca.hddt.dataapi.adapter.web.ApiResponse;
import vn.teca.hddt.dataapi.adapter.web.RequestContext;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.security.ApiPermission;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.config.HddtProperties;

/**
 * Cổng vào cho {@code /hthue/**} (SI-23, TD-API-40): X-Request-Id (UUID) bắt buộc — trừ khi bật ESB thì thiếu ⇒ tự sinh;
 * (tùy chọn) token gateway APIC; có khóa ESB ⇒ chữ ký x-partner/x-hmac như hddt-tthc-sharing (bắt buộc), không khóa ⇒
 * không xác thực (chỉ gọi nội bộ; không còn X-Client-Id/X-Client-Secret); bên gọi/quyền theo x-partner; giới hạn tốc độ;
 * giới hạn kích thước body. Ghi nhật ký truy cập (client, partner, request, route, mã trả về, thời gian) — không ghi MST.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ClientAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ClientRegistry registry;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper;
    private final HddtProperties props;
    private final MeterRegistry metrics;

    public ClientAuthenticationFilter(ClientRegistry registry, RateLimiter rateLimiter, ObjectMapper mapper,
                                      HddtProperties props, MeterRegistry metrics) {
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(RoutePermissions.BASE + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = req.getHeader(RequestContext.HEADER_REQUEST_ID);
        String partner = req.getHeader(registry.partnerHeader());
        String traceId = requestId != null && UUID_PATTERN.matcher(requestId).matches() ? requestId : UUID.randomUUID().toString();
        req.setAttribute(RequestContext.TRACE_ID, traceId);
        MDC.put("traceId", traceId);
        try {
            Optional<ErrorCode> rejection = check(req, requestId, partner);
            if (rejection.isPresent()) {
                reject(res, traceId, rejection.get());
            } else {
                chain.doFilter(new BodyLimitRequestWrapper(req, props.api().maxRequestBytes()), res);
            }
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            Object p = req.getAttribute(RequestContext.PRINCIPAL);
            String auditClient = p instanceof ClientPrincipal ? ((ClientPrincipal) p).clientId() : null;
            audit.info("client={} partner={} requestId={} method={} path={} status={} durationMs={}", safe(auditClient),
                    safe(partner), traceId, req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
            MDC.remove("traceId");
            MDC.remove("clientId");
        }
    }

    private Optional<ErrorCode> check(HttpServletRequest req, String requestId, String partner) {
        boolean noRequestId = requestId == null || requestId.isEmpty();
        // Bật ESB: thiếu X-Request-Id thì dùng traceId tự sinh (trục gọi như tthc-sharing chỉ gắn x-partner/x-hmac);
        // có gửi mà sai định dạng vẫn 400.
        if (noRequestId ? !registry.esbEnabled() : !UUID_PATTERN.matcher(requestId).matches()) {
            return Optional.of(ErrorCode.INVALID_HEADER);
        }
        if (registry.gatewayTokenHeader() != null && !registry.gatewayTokenValid(req.getHeader(registry.gatewayTokenHeader()))) {
            metrics.counter("hddt.auth.rejected", "reason", "gateway").increment();
            return Optional.of(ErrorCode.UNAUTHORIZED);
        }
        // TD-API-40 — y HmacFilter của hddt-tthc-sharing: có khóa ESB ⇒ x-partner + x-hmac đúng là bắt buộc và là cách xác
        // thực duy nhất. Không khóa ⇒ không xác thực (chỉ gọi nội bộ). X-Client-Id/X-Client-Secret đã bỏ (09/11).
        if (registry.esbEnabled() && !registry.esbValid(partner, req.getHeader(registry.esbHmacHeader()), Instant.now())) {
            metrics.counter("hddt.auth.rejected", "reason", "esb-hmac").increment();
            return Optional.of(ErrorCode.UNAUTHORIZED);
        }
        // Quyền theo bên gọi: x-partner → partner-clients (TTHC → tthc-hthue, TMS → tms-hthue).
        Optional<ClientPrincipal> principal = registry.principalFor(partner);
        if (!principal.isPresent()) {
            metrics.counter("hddt.auth.rejected", "reason", "partner").increment();
            return Optional.of(ErrorCode.UNAUTHORIZED);
        }
        String effectiveClientId = principal.get().clientId();
        MDC.put("clientId", effectiveClientId);
        Optional<ApiPermission> needed = RoutePermissions.required(req.getMethod(), req.getRequestURI());
        if (needed.isPresent() && !principal.get().has(needed.get())) {
            metrics.counter("hddt.auth.rejected", "reason", "permission").increment();
            return Optional.of(ErrorCode.FORBIDDEN);
        }
        HddtProperties.Security.Client cfg = registry.client(effectiveClientId).get();
        if (!rateLimiter.tryAcquire(effectiveClientId, cfg.ratePerSecond(), cfg.burst())) {
            metrics.counter("hddt.auth.rejected", "reason", "rate").increment();
            return Optional.of(ErrorCode.TOO_MANY_REQUESTS);
        }
        if (req.getContentLengthLong() > props.api().maxRequestBytes()) {
            return Optional.of(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        req.setAttribute(RequestContext.PRINCIPAL, principal.get());
        return Optional.empty();
    }

    private void reject(HttpServletResponse res, String traceId, ErrorCode code) throws IOException {
        res.setStatus(code.httpStatus());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiResponse.error(traceId, code, null));
    }

    private static String safe(String s) {
        return s == null ? "-" : s.replaceAll("[^A-Za-z0-9._-]", "?");
    }
}
