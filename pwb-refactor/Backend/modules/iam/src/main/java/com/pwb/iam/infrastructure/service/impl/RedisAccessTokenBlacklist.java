package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.AccessTokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAccessTokenBlacklist implements AccessTokenBlacklist {

    private static final String KEY_PREFIX = "iam:jwt:blacklist:";

    private final StringRedisTemplate redis;

    @Override
    public void blacklist(String jti, long expiresInSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long ttl = Math.max(expiresInSeconds, 1L);
        redis.opsForValue().set(KEY_PREFIX + jti, "1", java.time.Duration.ofSeconds(ttl));
        log.info("Access token blacklisted: jti={} ttl={}s", jti, ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = redis.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }
}