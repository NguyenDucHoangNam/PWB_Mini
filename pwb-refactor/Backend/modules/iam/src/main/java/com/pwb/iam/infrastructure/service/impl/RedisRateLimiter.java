package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "iam:ratelimit:";

    private final StringRedisTemplate redis;

    @Override
    public Decision consume(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            return Decision.allow(limit);
        }
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return Decision.allow(limit);
            }
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            long ttl = redis.getExpire(redisKey);
            long retryAfter = Math.max(ttl, 1L);
            if (count > limit) {
                log.warn("Rate limit exceeded: key={} count={} limit={}", key, count, limit);
                return Decision.deny(retryAfter);
            }
            return Decision.allow(Math.max(0L, limit - count));
        } catch (Exception ex) {
            log.warn("Redis unavailable, fail-open: key={} reason={}", key, ex.getMessage());
            return Decision.allow(limit);
        }
    }
}