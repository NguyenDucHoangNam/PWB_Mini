package com.pwb.backend.common.security.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtSignerTest {

    @Mock
    private JwtProperties jwtProperties;

    private JwtSigner jwtSigner;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getSecret()).thenReturn("this-is-a-test-secret-key-that-is-at-least-32-characters-long");
        lenient().when(jwtProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        lenient().when(jwtProperties.getIssuer()).thenReturn("pwb-mini");
        lenient().when(jwtProperties.getRefreshTokenTtlSeconds()).thenReturn(604800L);
        lenient().when(jwtProperties.getBlacklistClockSkewBufferSeconds()).thenReturn(30L);
        lenient().when(jwtProperties.getShadowGraceSeconds()).thenReturn(10L);

        jwtSigner = new JwtSigner(jwtProperties);
    }

    @Test
    void generateAndVerify_roundTrip() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String role = "USER";

        String token = jwtSigner.generateAccessToken(userId, email, role);

        assertNotNull(token);
        assertFalse(token.isBlank());

        JwtTypes.AuthenticatedUser authenticatedUser = jwtSigner.verifyAndExtract(token);

        assertNotNull(authenticatedUser);
        assertEquals(userId, authenticatedUser.userId());
        assertEquals(email, authenticatedUser.email());
        assertEquals(role, authenticatedUser.role());
    }

    @Test
    void extractSignature_consistent() {
        UUID userId = UUID.randomUUID();
        String token = jwtSigner.generateAccessToken(userId, "test@example.com", "USER");

        String signature1 = jwtSigner.extractSignature(token);
        String signature2 = jwtSigner.extractSignature(token);

        assertNotNull(signature1);
        assertEquals(signature1, signature2);
        assertEquals(64, signature1.length());
    }

    @Test
    void extractSignature_differentTokens_differentSignatures() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String token1 = jwtSigner.generateAccessToken(userId1, "user1@example.com", "USER");
        String token2 = jwtSigner.generateAccessToken(userId2, "user2@example.com", "USER");

        String sig1 = jwtSigner.extractSignature(token1);
        String sig2 = jwtSigner.extractSignature(token2);

        assertNotEquals(sig1, sig2);
    }

    @Test
    void extractSignature_nullToken_returnsNull() {
        assertNull(jwtSigner.extractSignature(null));
    }

    @Test
    void extractSignature_blankToken_returnsNull() {
        assertNull(jwtSigner.extractSignature("   "));
    }

    @Test
    void verifyAndExtract_wrongSignature_throwsJwtException() {
        UUID userId = UUID.randomUUID();
        String token = jwtSigner.generateAccessToken(userId, "test@example.com", "USER");

        String tamperedToken = token.substring(0, token.length() - 5) + "xxxxx";

        assertThrows(JwtException.class, () -> jwtSigner.verifyAndExtract(tamperedToken));
    }
}
