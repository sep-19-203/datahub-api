package vn.teca.hddt.dataapi.adapter.web.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Kiểm chữ ký ESB gắn khi gọi vào (TD-API-40) — cùng thuật toán {@code HmacFilter} của hddt-tthc-sharing:
 * {@code x-hmac = hex(HMAC-SHA256(khóa, yyyyMMdd + "." + x-partner))}, ngày theo múi giờ cấu hình (mặc định giờ VN).
 * Khác tthc-sharing: so constant-time, nhận hex hoa/thường, chấp nhận ngày của (now ± clockSkew) để không hỏng quanh nửa
 * đêm khi đồng hồ ESB và module lệch vài phút; có thể giới hạn danh sách đối tác.
 */
public final class EsbHmacVerifier {
    private static final String ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecretKeySpec key;
    private final ZoneId zone;
    private final Duration clockSkew;
    private final Set<String> allowedPartners = new LinkedHashSet<>();

    public EsbHmacVerifier(HddtProperties.Security.Esb cfg) {
        if (cfg == null || !cfg.enabled()) {
            throw new IllegalArgumentException("Chưa khai báo hddt.security.esb.hmac-key");
        }
        this.key = new SecretKeySpec(cfg.hmacKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.zone = ZoneId.of(cfg.zone());
        this.clockSkew = cfg.clockSkew() == null ? Duration.ZERO : cfg.clockSkew();
        List<String> allowed = cfg.allowedPartners();
        if (allowed != null) {
            for (String p : allowed) {
                if (p != null && !Jdk8Compat.isBlank(p)) {
                    allowedPartners.add(p.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    /** {@code true} khi đối tác được phép và chữ ký khớp với một trong các ngày hợp lệ quanh {@code now}. */
    public boolean verify(String partner, String hmacHex, Instant now) {
        if (partner == null || Jdk8Compat.isBlank(partner) || hmacHex == null || Jdk8Compat.isBlank(hmacHex)) {
            return false;
        }
        if (!allowedPartners.isEmpty() && !allowedPartners.contains(partner.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        byte[] given = hmacHex.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        boolean ok = false;
        for (LocalDate day : days(now)) {
            // không dừng sớm: thời gian so không phụ thuộc vị trí khớp
            ok |= MessageDigest.isEqual(given, sign(day, partner).getBytes(StandardCharsets.US_ASCII));
        }
        return ok;
    }

    /** Chữ ký ESB phải gửi cho {@code partner} vào ngày {@code day} (hex chữ thường) — dùng cho test/hướng dẫn. */
    public String sign(LocalDate day, String partner) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] raw = mac.doFinal((day.format(DAY) + "." + partner).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không tính được HMAC-SHA256", e);
        }
    }

    Set<LocalDate> days(Instant now) {
        Set<LocalDate> out = new LinkedHashSet<>();
        out.add(now.atZone(zone).toLocalDate());
        out.add(now.minus(clockSkew).atZone(zone).toLocalDate());
        out.add(now.plus(clockSkew).atZone(zone).toLocalDate());
        return out;
    }
}
