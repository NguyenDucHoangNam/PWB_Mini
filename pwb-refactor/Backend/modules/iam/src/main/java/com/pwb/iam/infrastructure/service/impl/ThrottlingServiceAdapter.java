package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.model.PasswordResetPolicy;
import com.pwb.iam.domain.service.ThrottlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottlingServiceAdapter implements ThrottlingService {

    private static final String RATE_LIMIT_PREFIX = "iam:ratelimit:";
    private static final String REGISTER_COOLDOWN_PREFIX = "iam:cooldown:register:";
    private static final String RESEND_COOLDOWN_PREFIX = "iam:cooldown:resend:";
    private static final String PASSWORD_RESET_COOLDOWN_PREFIX = "iam:password-reset:cooldown:";
    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final PasswordResetPolicy passwordResetPolicy;

    @Override
    public ThrottleDecision consume(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            return ThrottleDecision.allow(limit);
        }
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return ThrottleDecision.allow(limit);
            }
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            long ttl = redis.getExpire(redisKey);
            long retryAfter = Math.max(ttl, 1L);
            if (count > limit) {
                log.warn("Rate limit exceeded: key={} count={} limit={}", key, count, limit);
                return ThrottleDecision.deny(retryAfter);
            }
            return ThrottleDecision.allow(Math.max(0L, limit - count));
        } catch (Exception ex) {
            log.warn("Redis unavailable, fail-open: key={} reason={}", key, ex.getMessage());
            return ThrottleDecision.allow(limit);
        }
    }

    @Override
    public long enforceCooldown(String email, CooldownPurpose purpose) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String key = resolveCooldownKey(email, purpose);
        Duration cooldown = resolveCooldownDuration(purpose);
        return checkAndSetCooldown(key, cooldown);
    }

    private String resolveCooldownKey(String email, CooldownPurpose purpose) {
        String normalized = email.toLowerCase();
        return switch (purpose) {
            case REGISTER -> REGISTER_COOLDOWN_PREFIX + normalized;
            case RESEND_OTP -> RESEND_COOLDOWN_PREFIX + normalized;
            case PASSWORD_RESET -> PASSWORD_RESET_COOLDOWN_PREFIX + normalized;
        };
    }

    private Duration resolveCooldownDuration(CooldownPurpose purpose) {
        if (purpose == CooldownPurpose.PASSWORD_RESET) {
            return Duration.ofSeconds(passwordResetPolicy.cooldownSeconds());
        }
        return DEFAULT_COOLDOWN;
    }

    private long checkAndSetCooldown(String key, Duration cooldown) {
        try {
            Boolean set = redis.opsForValue().setIfAbsent(key, "1", cooldown);
            if (Boolean.FALSE.equals(set)) {
                Long ttl = redis.getExpire(key);
                long remaining = ttl != null && ttl > 0 ? ttl : cooldown.toSeconds();
                log.debug("Cooldown active: key={} remaining={}s", key, remaining);
                return remaining;
            }
            return 0L;
        } catch (Exception ex) {
            log.warn("Redis unavailable for cooldown check: key={} reason={}", key, ex.getMessage());
            return 0L;
        }
    }
}
