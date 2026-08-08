package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.model.PasswordResetPolicy;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.infrastructure.config.RateLimitProperties;
import com.pwb.shared.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottlingServiceAdapter implements ThrottlingService {

    private static final String RATE_LIMIT_PREFIX = "iam:ratelimit:";
    private static final String REGISTER_COOLDOWN_PREFIX = "iam:cooldown:register:";
    private static final String RESEND_COOLDOWN_PREFIX = "iam:cooldown:resend:";
    private static final String PASSWORD_RESET_COOLDOWN_PREFIX = "iam:password-reset:cooldown:";

    private static final String RATE_LIMIT_LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local count = redis.call('INCR', key)
            if count == 1 then
                redis.call('EXPIRE', key, window)
            end
            local ttl = redis.call('TTL', key)
            if count > limit then
                return {0, ttl}
            else
                return {1, limit - count}
            end
            """;

    private final StringRedisTemplate redis;
    private final PasswordResetPolicy passwordResetPolicy;
    private final OtpPolicy otpPolicy;
    private final RateLimitProperties rateLimitProperties;
    private final MeterRegistry meterRegistry;

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript rateLimitScript = new DefaultRedisScript<>();

    @PostConstruct
    @SuppressWarnings("rawtypes")
    void initLuaScript() {
        rateLimitScript.setScriptText(RATE_LIMIT_LUA_SCRIPT);
        rateLimitScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ThrottleDecision consume(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            return ThrottleDecision.allow(limit);
        }
        String redisKey = RATE_LIMIT_PREFIX + key;
        String scope = resolveScope(key);
        try {
            List<Object> result = (List<Object>) redis.execute(
                    rateLimitScript,
                    List.of(redisKey),
                    String.valueOf(limit),
                    String.valueOf(window.getSeconds())
            );
            if (result == null || result.size() < 2) {
                recordMetric(scope, "allow", "redis-error");
                return ThrottleDecision.allow(limit);
            }
            long allowed = ((Number) result.get(0)).longValue();
            long value = ((Number) result.get(1)).longValue();
            if (allowed == 0) {
                recordMetric(scope, "deny", "limit-exceeded");
                log.warn("Rate limit exceeded: key={} retryAfter={}s", key, value);
                return ThrottleDecision.deny(value);
            }
            recordMetric(scope, "allow", "ok");
            return ThrottleDecision.allow(value);
        } catch (Exception ex) {
            if (rateLimitProperties.isFailClosedForCriticalOps()) {
                recordMetric(scope, "deny", "fail-closed");
                log.error("Redis unavailable, fail-closed for critical operation: key={} reason={}", key, ex.getMessage());
                throw new BusinessException(IamErrorCode.SERVICE_UNAVAILABLE);
            }
            recordMetric(scope, "allow", "fail-open");
            log.warn("Redis unavailable, fail-open: key={} reason={}", key, ex.getMessage());
            return ThrottleDecision.allow(limit);
        }
    }

    @Override
    public long enforceCooldown(String email, CooldownPurpose purpose) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        return checkAndSetCooldown(
                resolveCooldownKey(email, purpose),
                resolveCooldownDuration(purpose),
                // Password reset mails to an arbitrary address; letting the cooldown lapse during
                // a Redis outage would turn the endpoint into an unmetered mail relay.
                purpose == CooldownPurpose.PASSWORD_RESET || rateLimitProperties.isFailClosedForCriticalOps());
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
        return switch (purpose) {
            case PASSWORD_RESET -> Duration.ofSeconds(passwordResetPolicy.cooldownSeconds());
            case RESEND_OTP, REGISTER -> otpPolicy.resendCooldown();
        };
    }

    private long checkAndSetCooldown(String key, Duration cooldown, boolean failClosed) {
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
            if (failClosed) {
                log.error("Redis unavailable, fail-closed for cooldown: key={} reason={}", key, ex.getMessage());
                throw new BusinessException(IamErrorCode.SERVICE_UNAVAILABLE);
            }
            log.warn("Redis unavailable, fail-open for cooldown: key={} reason={}", key, ex.getMessage());
            return 0L;
        }
    }

    private String resolveScope(String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        int colon = key.indexOf(':');
        return colon > 0 ? key.substring(0, colon) : key;
    }

    private void recordMetric(String scope, String decision, String reason) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("pwb.ratelimit.iam.decision")
                .tag("scope", scope)
                .tag("decision", decision)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}