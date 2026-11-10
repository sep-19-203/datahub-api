package vn.teca.hddt.dataapi.adapter.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.security.ApiPermission;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Danh bạ bên gọi (SI-23, TD-API-40). Từ 09/11 không còn X-Client-Id/X-Client-Secret (chỉ gọi nội bộ qua trục): bên gọi
 * xác định bằng header {@code x-partner} → {@code partner-clients} → hồ sơ client (quyền, phạm vi MST, rate-limit).
 * Có khóa ESB ⇒ x-partner phải kèm x-hmac đúng (như HmacFilter của hddt-tthc-sharing); không khóa ⇒ không xác thực.
 */
@Component
public class ClientRegistry {
    private static final Logger log = LoggerFactory.getLogger(ClientRegistry.class);
    private static final String DEFAULT_PARTNER_HEADER = "x-partner";

    private final Map<String, HddtProperties.Security.Client> clients = new HashMap<>();
    /** x-partner (chữ hoa) → client id. */
    private final Map<String, String> partnerClients = new HashMap<>();
    private final String defaultClient;
    private final byte[] gatewayTokenHash;
    private final String gatewayTokenHeader;
    private final String partnerHeader;
    private final HddtProperties.Security.Esb esb;
    private final EsbHmacVerifier esbVerifier;

    public ClientRegistry(HddtProperties props) {
        HddtProperties.Security s = props.security();
        if (s.clients() != null) {
            for (HddtProperties.Security.Client c : s.clients()) {
                if (c.id() != null && !Jdk8Compat.isBlank(c.id())) {
                    clients.put(c.id().trim(), c);
                }
            }
        }
        if (s.partnerClients() != null) {
            for (Map.Entry<String, String> e : s.partnerClients().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || Jdk8Compat.isBlank(e.getValue())) {
                    continue;
                }
                String id = e.getValue().trim();
                if (!clients.containsKey(id)) {
                    throw new IllegalStateException("partner-clients." + e.getKey() + " trỏ tới client không khai báo: " + id);
                }
                partnerClients.put(e.getKey().trim().toUpperCase(Locale.ROOT), id);
            }
        }
        this.defaultClient = s.defaultClient() == null || Jdk8Compat.isBlank(s.defaultClient()) ? null : s.defaultClient().trim();
        if (defaultClient != null && !clients.containsKey(defaultClient)) {
            throw new IllegalStateException("default-client trỏ tới client không khai báo: " + defaultClient);
        }
        this.gatewayTokenHeader = s.gatewayTokenHeader() == null || Jdk8Compat.isBlank(s.gatewayTokenHeader()) ? null : s.gatewayTokenHeader();
        this.gatewayTokenHash = s.gatewayTokenSha256() == null || Jdk8Compat.isBlank(s.gatewayTokenSha256()) ? null
                : Jdk8Compat.parseHex(s.gatewayTokenSha256().toLowerCase());
        if (gatewayTokenHeader != null && gatewayTokenHash == null) {
            throw new IllegalStateException("Đã khai báo gateway-token-header nhưng thiếu gateway-token-sha256");
        }
        this.partnerHeader = s.esb() != null && s.esb().partnerHeader() != null && !Jdk8Compat.isBlank(s.esb().partnerHeader())
                ? s.esb().partnerHeader() : DEFAULT_PARTNER_HEADER;
        this.esb = s.esb() != null && s.esb().enabled() ? s.esb() : null;
        this.esbVerifier = esb == null ? null : new EsbHmacVerifier(esb);
        if (esb == null) {
            log.warn("ESB HMAC TẮT (không có ESB_HMAC_KEY/tthc.hmac.key): /hthue/** KHÔNG xác thực — bên gọi theo {} {}, "
                    + "không gửi ⇒ client {}", partnerHeader, partnerClients, defaultClient == null ? "(từ chối)" : defaultClient);
        }
    }

    /** ESB HMAC đang bật (đã khai báo khóa) — TD-API-40. */
    public boolean esbEnabled() {
        return esbVerifier != null;
    }

    /** Tên header mã đối tác ({@code x-partner}) — luôn có, kể cả khi tắt ESB (dùng để chọn client/quyền). */
    public String partnerHeader() {
        return partnerHeader;
    }

    /** Tên header chữ ký ESB ({@code x-hmac}); {@code null} khi tắt. */
    public String esbHmacHeader() {
        return esb == null ? null : esb.hmacHeader();
    }

    public boolean esbValid(String partner, String hmac, Instant now) {
        return esbVerifier != null && esbVerifier.verify(partner, hmac, now);
    }

    public Optional<HddtProperties.Security.Client> client(String id) {
        return Optional.ofNullable(id == null ? null : clients.get(id));
    }

    /**
     * Bên gọi theo x-partner. Có x-partner ⇒ {@code partner-clients}, không khai báo ⇒ rỗng. Không có x-partner ⇒
     * {@code default-client} chỉ khi TẮT ESB (bật ESB thì x-partner đã bắt buộc qua chữ ký); không có ⇒ rỗng.
     */
    public Optional<ClientPrincipal> principalFor(String partner) {
        String id;
        if (partner != null && !Jdk8Compat.isBlank(partner)) {
            id = partnerClients.get(partner.trim().toUpperCase(Locale.ROOT));
        } else {
            id = esbEnabled() ? null : defaultClient;
        }
        HddtProperties.Security.Client c = id == null ? null : clients.get(id);
        return c == null ? Optional.empty() : Optional.of(principal(c));
    }

    private static ClientPrincipal principal(HddtProperties.Security.Client c) {
        Set<ApiPermission> perms = c.permissions() == null ? Jdk8Compat.setOf() : c.permissions();
        return new ClientPrincipal(c.id(), perms, c.allMst(), c.allowedMsts());
    }

    public String gatewayTokenHeader() {
        return gatewayTokenHeader;
    }

    public boolean gatewayTokenValid(String token) {
        return token != null && MessageDigest.isEqual(sha256(token), gatewayTokenHash);
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
