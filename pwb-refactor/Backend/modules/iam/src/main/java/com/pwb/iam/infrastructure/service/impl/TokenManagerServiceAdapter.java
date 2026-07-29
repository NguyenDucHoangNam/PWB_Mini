package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.config.JwtProperties;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenManagerServiceAdapter implements TokenManagerService {

    private static final String ACCESS_BLACKLIST_PREFIX = "iam:jwt:blacklist:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "iam:refresh:token:";
    private static final String REFRESH_USER_SET_PREFIX = "iam:refresh:user:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final RefreshTokenProperties refreshProperties;

    private SecretKey signingKey;

    @Override
    public AccessTokenInfo issueAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTtlSeconds());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .subject(user.getUserId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", user.getEmail() == null ? null : user.getEmail().value())
                .claim("role", user.getRole() == null ? null : user.getRole().name())
                .claim("status", user.getStatus() == null ? null : user.getStatus().name())
                .signWith(signingKey())
                .compact();

        log.debug("Access token issued: userId={} jti={}", user.getUserId(), jti);
        return new AccessTokenInfo(token, jti, expiresAt, jwtProperties.getAccessTtlSeconds());
    }

    @Override
    public RefreshTokenInfo issueRefreshToken(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        String raw = generateToken();
        String hash = sha256(raw);
        Duration ttl = Duration.ofSeconds(refreshProperties.getTtlSeconds());
        Instant expiresAt = Instant.now().plus(ttl);

        redis.opsForValue().set(tokenKey(hash), userId.toString(), ttl);
        redis.opsForSet().add(userSetKey(userId), hash);
        redis.expire(userSetKey(userId), ttl);

        log.info("Refresh token issued: userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return new RefreshTokenInfo(raw, userId, expiresAt, ttl);
    }

    @Override
    public RefreshTokenInfo rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        String hash = sha256(rawToken);
        String key = tokenKey(hash);
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) {
            throw new IllegalArgumentException("refresh token expired or invalid");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        Long ttlSeconds = redis.getExpire(key);
        redis.delete(key);
        redis.opsForSet().remove(userSetKey(userId), hash);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = refreshProperties.getTtlSeconds();
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        String newRaw = generateToken();
        String newHash = sha256(newRaw);
        Instant expiresAt = Instant.now().plus(ttl);
        redis.opsForValue().set(tokenKey(newHash), userId.toString(), ttl);
        redis.opsForSet().add(userSetKey(userId), newHash);
        redis.expire(userSetKey(userId), ttl);
        log.info("Refresh token rotated: userId={}", userId);
        return new RefreshTokenInfo(newRaw, userId, expiresAt, ttl);
    }

    @Override
    public void blacklistAccessToken(String jti, long expiresInSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long ttl = Math.max(expiresInSeconds, 1L);
        redis.opsForValue().set(ACCESS_BLACKLIST_PREFIX + jti, "1", java.time.Duration.ofSeconds(ttl));
        log.info("Access token blacklisted: jti={} ttl={}s", jti, ttl);
    }

    @Override
    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = sha256(rawToken);
        String key = tokenKey(hash);
        String userIdStr = redis.opsForValue().get(key);
        Boolean removed = redis.delete(key);
        if (userIdStr != null) {
            try {
                redis.opsForSet().remove(userSetKey(UUID.fromString(userIdStr)), hash);
            } catch (IllegalArgumentException ex) {
                log.debug("Invalid userId format in refresh token key: {}", userIdStr);
            }
        }
        log.info("Refresh token revoked: removed={}", removed);
    }

    @Override
    public void revokeAllRefreshTokensForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        String userSetKey = userSetKey(userId);
        Set<String> hashes = redis.opsForSet().members(userSetKey);
        if (hashes != null && !hashes.isEmpty()) {
            for (String hash : hashes) {
                redis.delete(tokenKey(hash));
            }
        }
        Boolean deleted = redis.delete(userSetKey);
        log.info("Revoked all refresh tokens for userId={} count={} setRemoved={}",
                userId, hashes == null ? 0 : hashes.size(), deleted);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redis.hasKey(ACCESS_BLACKLIST_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean isRefreshTokenRevoked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return true;
        }
        return redis.opsForValue().get(tokenKey(sha256(rawToken))) == null;
    }

    public Claims parseAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return null;
        }
    }

    private SecretKey signingKey() {
        if (signingKey == null) {
            byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    private String tokenKey(String hash) {
        return REFRESH_TOKEN_KEY_PREFIX + hash;
    }

    private String userSetKey(UUID userId) {
        return REFRESH_USER_SET_PREFIX + userId;
    }

    private String generateToken() {
        byte[] bytes = new byte[refreshProperties.getRawTokenBytes()];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static class Date {
        static java.util.Date from(Instant instant) {
            return java.util.Date.from(instant);
        }
    }
}
