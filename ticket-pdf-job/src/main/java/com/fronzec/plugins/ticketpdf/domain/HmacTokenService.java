package com.fronzec.plugins.ticketpdf.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stateless utility for producing HMAC-SHA256 signed QR tokens.
 *
 * <p>Token format:
 *
 * <pre>
 *   {ticketId}|{ticketCode}.{base64url_nopad(HMAC_SHA256(secret, "{ticketId}|{ticketCode}"))}
 * </pre>
 *
 * <p>Uses only JDK cryptography — no external dependencies.
 */
public final class HmacTokenService {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacTokenService() {
        // utility class
    }

    /**
     * Sign a ticket and return the full token string.
     *
     * @param ticketId   the {@code event_tickets.id} value
     * @param ticketCode the {@code event_tickets.ticket_code} value
     * @param secret     the {@code TOKEN_SECRET} job parameter
     * @return signed token suitable for embedding in a QR code
     */
    public static String sign(long ticketId, String ticketCode, String secret) {
        String payload = ticketId + "|" + ticketCode;
        String signature = hmacBase64Url(secret, payload);
        return payload + "." + signature;
    }

    private static String hmacBase64Url(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] sigBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 signing failed", e);
        }
    }
}
