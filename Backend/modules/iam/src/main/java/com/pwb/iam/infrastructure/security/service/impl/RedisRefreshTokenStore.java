package com.pwb.iam.infrastructure.security.service.impl;

import com.pwb.iam.infrastructure.security.service.RefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_INDEX_PREFIX = "refresh:user:";

    @Nullable
    @Autowired
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public RedisRefreshTokenStore(@Nullable StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void store(String jti, UUID userId, long ttlSeconds) {
        if (stringRedisTemplate == null) {
            return;
        }
        Duration ttl = Duration.ofSeconds(Math.max(1L, ttlSeconds));
        String userIdValue = userId.toString();
        stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                operations.opsForValue().set(TOKEN_KEY_PREFIX + jti, userIdValue, ttl);
                operations.opsForSet().add(USER_INDEX_PREFIX + userIdValue, jti);
                operations.expire(USER_INDEX_PREFIX + userIdValue, ttl);
                return null;
            }
        });
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
        Set<String> keysToDelete = new HashSet<>();

        if (jtis != null) {
            for (String jti : jtis) {
                keysToDelete.add(TOKEN_KEY_PREFIX + jti);
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
                    keysToDelete.add(key);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed SCAN during revokeAllForUser: userId={} reason={}", userId, ex.getMessage());
        }

        if (!keysToDelete.isEmpty()) {
            keysToDelete.add(indexKey);
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) {
                    for (String key : keysToDelete) {
                        operations.delete(key);
                    }
                    return null;
                }
            });
        } else {
            stringRedisTemplate.delete(indexKey);
        }
        log.info("Revoked all refresh tokens for user: userId={} count={}", userId, keysToDelete.size() - 1);
    }
}
