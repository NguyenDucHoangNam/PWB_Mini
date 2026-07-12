package com.pwb.backend.modules.share.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShareTokenRateLimiter {

    private static final String KEY_PREFIX = "ratelimit:share:";
    private static final long SHARED_ENDPOINT_PERMITS = 120L;
    private static final long KEYS_ENDPOINT_PERMITS = 600L;
    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final RedissonClient redissonClient;

    public boolean tryAcquireShared(UUID shareToken) {
        return tryAcquire(shareToken, "shared", SHARED_ENDPOINT_PERMITS);
    }

    public boolean tryAcquireKeys(UUID shareToken) {
        return tryAcquire(shareToken, "keys", KEYS_ENDPOINT_PERMITS);
    }

    private boolean tryAcquire(UUID shareToken, String endpoint, long permits) {
        if (shareToken == null) {
            return true;
        }
        String key = KEY_PREFIX + shareToken + ":" + endpoint;
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, permits, WINDOW_MILLIS, RateIntervalUnit.MILLISECONDS);
            return limiter.tryAcquire(1);
        } catch (Exception ex) {
            log.warn("SHARE_TOKEN_RATE_LIMIT_FAILED shareToken={} endpoint={} reason={}",
                    shareToken, endpoint, ex.getMessage());
            return true;
        }
    }
}
