package com.pwb.backend.auth;

import com.pwb.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtSigner {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SECRET_MIN_BYTES = 32;
    private static final int JTI_BYTE_LENGTH = 16;
    private static final int UUID_HEX_LENGTH = 32;
    private static final int UUID_DASH_LENGTH = 4;
    private static final int UUID_STRING_LENGTH = UUID_HEX_LENGTH + UUID_DASH_LENGTH;
    private static final int[] UUID_DASH_POSITIONS = {8, 13, 18, 23};
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtSigner(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < SECRET_MIN_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenTtlSeconds());
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(properties.getIssuer())
                .claim("email", email)
                .claim("role", role)
                .id(generateJti())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AuthenticatedUser verifyAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        return new AuthenticatedUser(userId, email, role);
    }

    private String generateJti() {
        byte[] bytes = new byte[JTI_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        char[] hex = new char[UUID_STRING_LENGTH];
        int j = 0;
        int dashIndex = 0;
        for (int i = 0; i < JTI_BYTE_LENGTH; i++) {
            hex[j++] = HEX_CHARS[(bytes[i] & 0xF0) >>> 4];
            hex[j++] = HEX_CHARS[bytes[i] & 0x0F];
            if (dashIndex < UUID_DASH_POSITIONS.length && j == UUID_DASH_POSITIONS[dashIndex]) {
                hex[j++] = '-';
                dashIndex++;
            }
        }
        return new String(hex);
    }
}