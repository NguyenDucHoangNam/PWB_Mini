package com.pwb.iam.infrastructure.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_TYPE_ACCESS = "ACCESS";
    private static final String CLAIM_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    private SecretKey accessTokenKey;
    private SecretKey refreshTokenKey;

    @PostConstruct
    void init() {
        String accessSecret = jwtProperties.getSecret();
        String refreshSecret = jwtProperties.getRefreshSecret();

        validateSecret(accessSecret, "app.security.jwt.secret");
        validateSecret(refreshSecret, "app.security.jwt.refresh-secret");

        accessTokenKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        refreshTokenKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String roleName) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, roleName)
                .claim(CLAIM_TYPE, CLAIM_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(accessTokenKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(CLAIM_TYPE, CLAIM_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(refreshTokenKey)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, accessTokenKey);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshTokenKey);
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpiration() / 1000L;
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token, accessTokenKey).getSubject());
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        return UUID.fromString(extractClaims(token, refreshTokenKey).getSubject());
    }

    private void validateSecret(String secret, String propertyName) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    propertyName + " is missing. Generate with: openssl rand -hex 32");
        }
        int byteLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    propertyName + " must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256 (got " + byteLength + " bytes). "
                            + "Generate with: openssl rand -hex 32");
        }
        try {
            Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException ex) {
            throw new IllegalStateException(
                    propertyName + " is too weak: " + ex.getMessage(), ex);
        }
    }

    private boolean validateToken(String token, SecretKey key) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    private Claims extractClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
