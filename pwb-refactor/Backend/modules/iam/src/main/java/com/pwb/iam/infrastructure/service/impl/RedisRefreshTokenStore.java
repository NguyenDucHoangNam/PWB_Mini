package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenManager {

    private static final String KEY_PREFIX = "iam:refresh:token:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final RefreshTokenProperties properties;

    @Override
    public RefreshToken issue(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        String raw = generateToken();
        String hash = sha256(raw);
        Duration ttl = Duration.ofSeconds(properties.getTtlSeconds());
        Instant expiresAt = Instant.now().plus(ttl);
        String key = KEY_PREFIX + hash;
        redis.opsForValue().set(key, userId.toString(), ttl);
        log.info("Refresh token issued: userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return new RefreshToken(raw, userId, expiresAt, ttl);
    }

    @Override
    public RefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalStateException("REFRESH_TOKEN_INVALID");
        }
        String hash = sha256(rawToken);
        String key = KEY_PREFIX + hash;
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) {
            throw new IllegalStateException("REFRESH_TOKEN_EXPIRED");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("REFRESH_TOKEN_INVALID");
        }
        Long ttlSeconds = redis.getExpire(key);
        redis.delete(key);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = properties.getTtlSeconds();
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        String newRaw = generateToken();
        String newHash = sha256(newRaw);
        Instant expiresAt = Instant.now().plus(ttl);
        redis.opsForValue().set(KEY_PREFIX + newHash, userId.toString(), ttl);
        log.info("Refresh token rotated: userId={}", userId);
        return new RefreshToken(newRaw, userId, expiresAt, ttl);
    }

    @Override
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = sha256(rawToken);
        Boolean removed = redis.delete(KEY_PREFIX + hash);
        log.info("Refresh token revoked: removed={}", removed);
    }

    @Override
    public boolean isRevoked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return true;
        }
        return redis.opsForValue().get(KEY_PREFIX + sha256(rawToken)) == null;
    }

    public Optional<UUID> peekUserId(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String userIdStr = redis.opsForValue().get(KEY_PREFIX + sha256(rawToken));
        if (userIdStr == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userIdStr));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[properties.getRawTokenBytes()];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}