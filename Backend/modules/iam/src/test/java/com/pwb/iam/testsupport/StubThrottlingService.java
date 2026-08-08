package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.ThrottlingService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StubThrottlingService implements ThrottlingService {

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Integer> rateLimitCounters = new ConcurrentHashMap<>();
    private boolean rateLimitAllow = true;
    private long rateLimitRetryAfter = 0L;

    public StubThrottlingService presetAllow(boolean allow, long retryAfterSeconds) {
        this.rateLimitAllow = allow;
        this.rateLimitRetryAfter = retryAfterSeconds;
        return this;
    }

    public StubThrottlingService presetCooldown(String email, CooldownPurpose purpose, long remainingSeconds) {
        String key = email.toLowerCase() + ":" + purpose.name();
        cooldowns.put(key, remainingSeconds);
        return this;
    }

    @Override
    public ThrottleDecision consume(String key, int limit, Duration window) {
        if (!rateLimitAllow) {
            return ThrottleDecision.deny(rateLimitRetryAfter);
        }
        int count = rateLimitCounters.merge(key, 1, Integer::sum);
        long remaining = Math.max(0L, limit - count);
        return ThrottleDecision.allow(remaining);
    }

    @Override
    public long enforceCooldown(String email, CooldownPurpose purpose) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String key = email.toLowerCase() + ":" + purpose.name();
        Long remaining = cooldowns.get(key);
        if (remaining != null && remaining > 0) {
            return remaining;
        }
        return 0L;
    }

    public void reset() {
        cooldowns.clear();
        rateLimitCounters.clear();
        rateLimitAllow = true;
        rateLimitRetryAfter = 0L;
    }
}
