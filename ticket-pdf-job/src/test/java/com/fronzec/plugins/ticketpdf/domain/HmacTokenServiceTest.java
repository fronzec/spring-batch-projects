package com.fronzec.plugins.ticketpdf.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacTokenServiceTest {

    private static final long TICKET_ID = 7L;
    private static final String TICKET_CODE = "ABC123";
    // >= 32 bytes: HmacTokenService enforces a minimum key strength.
    private static final String SECRET = "unit-test-secret-please-use-32+bytes!";

    /** Recompute HMAC-SHA256 independently for assertion. */
    private String computeHmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sigBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
    }

    @Test
    void sign_isDeterministic_sameInputsProduceSameToken() {
        String token1 = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);
        String token2 = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);

        assertThat(token1).isEqualTo(token2);
    }

    @Test
    void sign_tokenFormat_leftOfLastDotIsTicketIdPipeTicketCode() {
        String token = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);

        int lastDot = token.lastIndexOf('.');
        assertThat(lastDot).isGreaterThan(0);

        String leftPart = token.substring(0, lastDot);
        assertThat(leftPart).isEqualTo(TICKET_ID + "|" + TICKET_CODE);
    }

    @Test
    void sign_signatureMatchesManualHmacComputation() throws Exception {
        String token = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);

        int lastDot = token.lastIndexOf('.');
        String embeddedSig = token.substring(lastDot + 1);

        String payload = TICKET_ID + "|" + TICKET_CODE;
        String expectedSig = computeHmac(SECRET, payload);

        assertThat(embeddedSig).isEqualTo(expectedSig);
    }

    @Test
    void sign_differentSecret_producesDifferentSignature() {
        String token1 = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);
        String token2 =
            HmacTokenService.sign(TICKET_ID, TICKET_CODE, "a-different-wrong-secret-32+bytes-xx!");

        // Left part is identical (same ticketId + ticketCode), but signature differs
        int dot1 = token1.lastIndexOf('.');
        int dot2 = token2.lastIndexOf('.');
        assertThat(token1.substring(0, dot1)).isEqualTo(token2.substring(0, dot2));
        assertThat(token1.substring(dot1 + 1)).isNotEqualTo(token2.substring(dot2 + 1));
    }

    @Test
    void sign_differentTicketId_producesDifferentToken() {
        String token1 = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);
        String token2 = HmacTokenService.sign(TICKET_ID + 1, TICKET_CODE, SECRET);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void sign_rejectsBlankSecret() {
        assertThatThrownBy(() -> HmacTokenService.sign(TICKET_ID, TICKET_CODE, "   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sign_rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> HmacTokenService.sign(TICKET_ID, TICKET_CODE, "too-short"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sign_signatureIsBase64UrlNoPadding() {
        String token = HmacTokenService.sign(TICKET_ID, TICKET_CODE, SECRET);

        int lastDot = token.lastIndexOf('.');
        String sig = token.substring(lastDot + 1);

        // Base64url characters: A-Z a-z 0-9 - _ (no +, /, or =)
        assertThat(sig).matches("[A-Za-z0-9_-]+");
    }
}
