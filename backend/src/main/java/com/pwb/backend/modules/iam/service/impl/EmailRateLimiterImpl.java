package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.EmailRateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class EmailRateLimiterImpl implements EmailRateLimiter {

    private static final String REGISTER_ATTEMPT_PREFIX = "email_register_attempts:";
    private static final String REGISTER_LOCKOUT_PREFIX = "email_register_lockout:";
    private static final String RESEND_ATTEMPT_PREFIX = "email_resend_attempts:";
    private static final String RESEND_LOCKOUT_PREFIX = "email_resend_lockout:";
    private static final String FORGOT_ATTEMPT_PREFIX = "email_forgot_attempts:";
    private static final String FORGOT_LOCKOUT_PREFIX = "email_forgot_lockout:";
    private static final String SCRIPT_RESOURCE = "scripts/email_rate_limit.lua";

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<List> script;

    @Value("${app.iam.rate-limit.register.max-attempts:5}")
    private int registerMaxAttempts;
    @Value("${app.iam.rate-limit.register.window-seconds:900}")
    private long registerWindowSeconds;
    @Value("${app.iam.rate-limit.register.lockout-seconds:1800}")
    private long registerLockoutSeconds;

    @Value("${app.iam.rate-limit.resend.max-attempts:5}")
    private int resendMaxAttempts;
    @Value("${app.iam.rate-limit.resend.window-seconds:3600}")
    private long resendWindowSeconds;
    @Value("${app.iam.rate-limit.resend.lockout-seconds:1800}")
    private long resendLockoutSeconds;

    @Value("${app.iam.rate-limit.forgot.max-attempts:3}")
    private int forgotMaxAttempts;
    @Value("${app.iam.rate-limit.forgot.window-seconds:3600}")
    private long forgotWindowSeconds;
    @Value("${app.iam.rate-limit.forgot.lockout-seconds:1800}")
    private long forgotLockoutSeconds;

    public EmailRateLimiterImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void loadScript() {
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(SCRIPT_RESOURCE).getInputStream(),
                    StandardCharsets.UTF_8);
            this.script = new DefaultRedisScript<>(body, List.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + SCRIPT_RESOURCE, ex);
        }
    }

    @Override
    public void checkRegisterAttempt(String email) {
        enforce(REGISTER_ATTEMPT_PREFIX, REGISTER_LOCKOUT_PREFIX,
                email, registerMaxAttempts, registerWindowSeconds, registerLockoutSeconds,
                "REGISTER_RATE_LIMIT");
    }

    @Override
    public void checkResendOtpAttempt(String email) {
        enforce(RESEND_ATTEMPT_PREFIX, RESEND_LOCKOUT_PREFIX,
                email, resendMaxAttempts, resendWindowSeconds, resendLockoutSeconds,
                "RESEND_OTP_RATE_LIMIT");
    }

    @Override
    public void checkForgotPasswordAttempt(String email) {
        enforce(FORGOT_ATTEMPT_PREFIX, FORGOT_LOCKOUT_PREFIX,
                email, forgotMaxAttempts, forgotWindowSeconds, forgotLockoutSeconds,
                "FORGOT_PASSWORD_RATE_LIMIT");
    }

    @Override
    public void clearRegisterAttempts(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = normalize(email);
        redisTemplate.delete(REGISTER_ATTEMPT_PREFIX + normalized);
        redisTemplate.delete(REGISTER_LOCKOUT_PREFIX + normalized);
    }

    private void enforce(String attemptPrefix,
                         String lockoutPrefix,
                         String email,
                         int maxAttempts,
                         long windowSeconds,
                         long lockoutSeconds,
                         String operation) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = normalize(email);
        String attemptKey = attemptPrefix + normalized;
        String lockoutKey = lockoutPrefix + normalized;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            Long ttl = redisTemplate.getExpire(lockoutKey);
            long retryAfter = ttl == null || ttl < 0 ? lockoutSeconds : ttl;
            log.warn("EMAIL_RATE_LIMITED operation={} emailHash={} retryAfterSeconds={}",
                    operation, normalized.hashCode(), retryAfter);
            throw new BusinessException(
                    IamErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests for this email. Retry after " + retryAfter + " seconds",
                    null,
                    Map.of("retryAfterSeconds", retryAfter));
        }

        List<Object> result;
        try {
            result = redisTemplate.execute(
                    script,
                    List.of(attemptKey, lockoutKey),
                    Integer.toString(maxAttempts),
                    Long.toString(windowSeconds),
                    Long.toString(lockoutSeconds));
        } catch (Exception ex) {
            log.warn("EMAIL_RATE_LIMIT_REDIS_FAILURE operation={} error={}", operation, ex.getMessage());
            return;
        }
        if (result == null || result.size() < 2) {
            return;
        }
        long locked = ((Number) result.get(1)).longValue();
        if (locked == 1L) {
            log.warn("EMAIL_RATE_LIMIT_TRIGGERED operation={} emailHash={} lockoutSeconds={}",
                    operation, normalized.hashCode(), lockoutSeconds);
            throw new BusinessException(
                    IamErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests for this email. Retry after " + lockoutSeconds + " seconds",
                    null,
                    Map.of("retryAfterSeconds", lockoutSeconds));
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}