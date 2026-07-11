package com.pwb.backend.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSignerTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    private JwtSigner buildSigner() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("pwb-mini");
        properties.setAccessTokenTtlSeconds(900);
        return new JwtSigner(properties);
    }

    @Test
    void generateAndVerifyRoundTrip() {
        JwtSigner signer = buildSigner();
        UUID userId = UUID.randomUUID();

        String token = signer.generateAccessToken(userId, "alice@example.com", "ROLE_USER");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        AuthenticatedUser extracted = signer.verifyAndExtract(token);

        assertThat(extracted.userId()).isEqualTo(userId);
        assertThat(extracted.email()).isEqualTo("alice@example.com");
        assertThat(extracted.role()).isEqualTo("ROLE_USER");
    }

    @Test
    void verifyFailsOnTamperedSignature() {
        JwtSigner signer = buildSigner();
        UUID userId = UUID.randomUUID();
        String token = signer.generateAccessToken(userId, "bob@example.com", "ROLE_USER");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> signer.verifyAndExtract(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void secretShorterThan32BytesRejected() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        properties.setIssuer("pwb-mini");
        properties.setAccessTokenTtlSeconds(900);

        assertThatThrownBy(() -> new JwtSigner(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}