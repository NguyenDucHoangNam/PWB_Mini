package com.pwb.iam.infrastructure.security.service.impl;

import com.pwb.iam.infrastructure.security.config.RateLimitProperties;
import com.pwb.iam.infrastructure.security.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService implements RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String UNKNOWN_KEY = "unknown";

    private final RateLimitProperties rateLimitProperties;

    @Nullable
    @Autowired
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public RateLimitDecision check(String endpoint, String clientKey) {
        if (!rateLimitProperties.isEnabled() || stringRedisTemplate == null) {
            return RateLimitDecision.allow();
        }

        String key = buildKey(endpoint, clientKey);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, WINDOW);
        }

        if (count != null && count > rateLimitProperties.getRequestsPerHour()) {
            long retryAfter = getRemainingSeconds(key);
            log.warn("Rate limit exceeded: endpoint={} key={} count={} retryAfter={}s",
                    endpoint, clientKey, count, retryAfter);
            return RateLimitDecision.deny(retryAfter);
        }

        return RateLimitDecision.allow();
    }

    @Override
    public void reset(String endpoint, String clientKey) {
        if (stringRedisTemplate == null) {
            return;
        }
        stringRedisTemplate.delete(buildKey(endpoint, clientKey));
    }

    private String buildKey(String endpoint, String clientKey) {
        String safeEndpoint = endpoint == null || endpoint.isBlank() ? UNKNOWN_KEY : endpoint;
        String safeKey = clientKey == null || clientKey.isBlank() ? UNKNOWN_KEY : clientKey;
        return KEY_PREFIX + safeEndpoint + ":" + safeKey;
    }

    private long getRemainingSeconds(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : WINDOW.toSeconds();
    }
}
