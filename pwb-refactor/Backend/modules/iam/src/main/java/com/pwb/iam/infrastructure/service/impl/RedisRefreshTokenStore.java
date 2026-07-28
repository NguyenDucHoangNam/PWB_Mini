package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.exception.RefreshTokenExpiredException;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenManager {

    private static final String TOKEN_KEY_PREFIX = "iam:refresh:token:";
    private static final String USER_SET_PREFIX = "iam:refresh:user:";
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
        String tokenKey = tokenKey(hash);
        String userSetKey = userSetKey(userId);

        redis.opsForValue().set(tokenKey, userId.toString(), ttl);
        redis.opsForSet().add(userSetKey, hash);
        redis.expire(userSetKey, ttl);

        log.info("Refresh token issued: userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return new RefreshToken(raw, userId, expiresAt, ttl);
    }

    @Override
    public RefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenInvalidException();
        }
        String hash = sha256(rawToken);
        String key = tokenKey(hash);
        String userIdStr = redis.opsForValue().get(key);
        if (userIdStr == null) {
            throw new RefreshTokenExpiredException();
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new RefreshTokenInvalidException();
        }
        Long ttlSeconds = redis.getExpire(key);
        redis.delete(key);
        redis.opsForSet().remove(userSetKey(userId), hash);
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = properties.getTtlSeconds();
        }
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        String newRaw = generateToken();
        String newHash = sha256(newRaw);
        Instant expiresAt = Instant.now().plus(ttl);
        redis.opsForValue().set(tokenKey(newHash), userId.toString(), ttl);
        redis.opsForSet().add(userSetKey(userId), newHash);
        redis.expire(userSetKey(userId), ttl);
        log.info("Refresh token rotated: userId={}", userId);
        return new RefreshToken(newRaw, userId, expiresAt, ttl);
    }

    @Override
    public void revoke(String rawToken) {
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
    public void revokeAllForUser(UUID userId) {
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
    public boolean isRevoked(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return true;
        }
        return redis.opsForValue().get(tokenKey(sha256(rawToken))) == null;
    }

    public Optional<UUID> peekUserId(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String userIdStr = redis.opsForValue().get(tokenKey(sha256(rawToken)));
        if (userIdStr == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userIdStr));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String tokenKey(String hash) {
        return TOKEN_KEY_PREFIX + hash;
    }

    private String userSetKey(UUID userId) {
        return USER_SET_PREFIX + userId;
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
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new RefreshTokenInvalidException("SHA-256 not available");
        }
    }
}