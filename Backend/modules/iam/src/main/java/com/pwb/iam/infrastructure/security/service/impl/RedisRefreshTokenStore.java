package com.pwb.iam.infrastructure.security.service.impl;

import com.pwb.iam.infrastructure.security.service.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_INDEX_PREFIX = "refresh:user:";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void store(String jti, UUID userId, long ttlSeconds) {
        if (stringRedisTemplate == null) {
            return;
        }
        Duration ttl = Duration.ofSeconds(Math.max(1L, ttlSeconds));
        String userIdValue = userId.toString();
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + jti, userIdValue, ttl);
        stringRedisTemplate.opsForSet().add(USER_INDEX_PREFIX + userIdValue, jti);
        stringRedisTemplate.expire(USER_INDEX_PREFIX + userIdValue, ttl);
    }

    @Override
    public Optional<UUID> findUserId(String jti) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + jti);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String jti) {
        if (stringRedisTemplate == null) {
            return;
        }
        String tokenKey = TOKEN_KEY_PREFIX + jti;
        String userIdValue = stringRedisTemplate.opsForValue().get(tokenKey);
        stringRedisTemplate.delete(tokenKey);
        if (userIdValue != null && !userIdValue.isBlank()) {
            stringRedisTemplate.opsForSet().remove(USER_INDEX_PREFIX + userIdValue, jti);
        }
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        if (stringRedisTemplate == null) {
            return;
        }
        String indexKey = USER_INDEX_PREFIX + userId;
        Set<String> jtis = stringRedisTemplate.opsForSet().members(indexKey);
        Set<String> tokenKeys = new HashSet<>();
        if (jtis != null) {
            for (String jti : jtis) {
                tokenKeys.add(TOKEN_KEY_PREFIX + jti);
            }
        }

        ScanOptions options = ScanOptions.scanOptions()
                .match(TOKEN_KEY_PREFIX + "*")
                .count(200)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = stringRedisTemplate.opsForValue().get(key);
                if (value != null && value.equals(userId.toString())) {
                    tokenKeys.add(key);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed SCAN during revokeAllForUser: userId={} reason={}", userId, ex.getMessage());
        }

        if (!tokenKeys.isEmpty()) {
            stringRedisTemplate.delete(tokenKeys);
        }
        stringRedisTemplate.delete(indexKey);
        log.info("Revoked all refresh tokens for user: userId={} count={}", userId, tokenKeys.size());
    }
}
