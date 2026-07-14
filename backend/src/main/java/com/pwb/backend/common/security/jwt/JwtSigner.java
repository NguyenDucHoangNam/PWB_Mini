package com.pwb.backend.common.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
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
    private static final String SHA_256 = "SHA-256";

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

    public JwtTypes.AuthenticatedUser verifyAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        return new JwtTypes.AuthenticatedUser(userId, email, role);
    }

    public UUID parseExpiredTokenUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new ExpiredJwtException(null, null, "Token is empty");
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token);
            throw new IllegalStateException("Token is not expired; use verifyAndExtract instead");
        } catch (ExpiredJwtException ex) {
            Claims claims = ex.getClaims();
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new JwtException("Missing subject");
            }
            return UUID.fromString(subject);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid refresh handshake: " + ex.getMessage(), ex);
        }
    }

    public String extractSignature(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    public long extractExpiryEpochSecond(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Token is empty");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            claims = ex.getClaims();
        }
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new JwtException("Missing expiration claim");
        }
        return expiration.toInstant().getEpochSecond();
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
