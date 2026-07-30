package com.pwb.web.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpRateLimitService {

    private static final String RATE_LIMIT_PREFIX = "iam:ratelimit:global:";

    private final StringRedisTemplate redis;

    public RateLimitResult checkRateLimit(String clientIp, int limit, Duration window) {
        String key = RATE_LIMIT_PREFIX + clientIp;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return RateLimitResult.allow(limit);
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            if (count > limit) {
                Long ttl = redis.getExpire(key);
                long retryAfter = Math.max(ttl != null ? ttl : window.getSeconds(), 1L);
                log.warn("Global rate limit exceeded: ip={} count={} limit={}", clientIp, count, limit);
                return RateLimitResult.deny(retryAfter);
            }
            return RateLimitResult.allow(limit - count);
        } catch (Exception ex) {
            log.warn("Redis unavailable for rate limit check: ip={} reason={}", clientIp, ex.getMessage());
            return RateLimitResult.allow(limit);
        }
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds, long remaining) {
        public static RateLimitResult allow(long remaining) {
            return new RateLimitResult(true, 0L, remaining);
        }

        public static RateLimitResult deny(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds, 0L);
        }
    }
}
