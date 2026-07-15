package com.pwb.backend.security.jwt;

import com.pwb.backend.security.CustomUserDetails;
import com.pwb.backend.security.config.SecurityProperties;
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

    private final SecurityProperties securityProperties;

    private SecretKey accessTokenKey;
    private SecretKey refreshTokenKey;

    @PostConstruct
    void init() {
        String accessSecret = securityProperties.getJwt().getSecret();
        String refreshSecret = securityProperties.getJwt().getRefreshSecret();

        validateSecret(accessSecret, "app.security.jwt.secret");
        validateSecret(refreshSecret, "app.security.jwt.refresh-secret");

        accessTokenKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        refreshTokenKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
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

    public String generateAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() +
                securityProperties.getJwt().getAccessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getId().toString())
                .claim("email", userDetails.getUsername())
                .claim("role", extractRoleName(userDetails))
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(accessTokenKey)
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() +
                securityProperties.getJwt().getRefreshTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getId().toString())
                .claim("type", "REFRESH")
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

    public UUID extractUserId(String token) {
        Claims claims = extractClaims(token, accessTokenKey);
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        Claims claims = extractClaims(token, refreshTokenKey);
        return UUID.fromString(claims.getSubject());
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

    private String extractRoleName(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("USER");
    }
}
