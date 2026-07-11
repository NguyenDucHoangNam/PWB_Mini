package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.modules.iam.config.LoginProperties;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String ATTEMPT_KEY_PREFIX = "login_attempts:";
    private static final String LOCKOUT_KEY_PREFIX = "login_lockout:";
    private static final String SCRIPT_RESOURCE = "scripts/login_attempt.lua";

    private final StringRedisTemplate redisTemplate;
    private final LoginProperties properties;
    private final DefaultRedisScript<List> script;

    public LoginAttemptServiceImpl(StringRedisTemplate redisTemplate, LoginProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(List.class);
        try {
            String body = StreamUtils.copyToString(
                    new ClassPathResource(SCRIPT_RESOURCE).getInputStream(),
                    StandardCharsets.UTF_8);
            this.script.setScriptText(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + SCRIPT_RESOURCE, ex);
        }
    }

    @PostConstruct
    void warmUp() {
        // Script body already cached in constructor.
    }

    @Override
    public void validateNotLocked(UUID userId) {
        Boolean exists = redisTemplate.hasKey(LOCKOUT_KEY_PREFIX + userId);
        if (Boolean.TRUE.equals(exists)) {
            Long ttl = redisTemplate.getExpire(LOCKOUT_KEY_PREFIX + userId, TimeUnit.SECONDS);
            long seconds = ttl == null || ttl < 0 ? properties.getLockoutSeconds() : ttl;
            throw new BusinessException(
                    IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
                    "Account temporarily locked. Retry after " + seconds + " seconds",
                    null,
                    Map.of("retryAfterSeconds", seconds));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void recordFailure(UUID userId) {
        String attemptKey = ATTEMPT_KEY_PREFIX + userId;
        String lockoutKey = LOCKOUT_KEY_PREFIX + userId;
        List<Object> result;
        try {
            result = redisTemplate.execute(
                    script,
                    List.of(attemptKey, lockoutKey),
                    Integer.toString(properties.getMaxFailedAttempts()),
                    Long.toString(properties.getFailedAttemptWindowSeconds()),
                    Long.toString(properties.getLockoutSeconds()));
        } catch (Exception ex) {
            log.warn("Failed to record login attempt for {}: {}", userId, ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        if (result == null || result.size() < 2) {
            return;
        }
        long locked = ((Number) result.get(1)).longValue();
        if (locked == 1L) {
            long seconds = properties.getLockoutSeconds();
            log.warn("ACCOUNT_LOCKED_TEMPORARY userId={} lockoutSeconds={}", userId, seconds);
            throw new BusinessException(
                    IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
                    "Account temporarily locked due to too many failed attempts",
                    null,
                    Map.of("retryAfterSeconds", seconds));
        }
    }

    @Override
    public void clearFailures(UUID userId) {
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + userId);
        redisTemplate.delete(LOCKOUT_KEY_PREFIX + userId);
    }

    @Override
    public long lockoutRetryAfterSeconds() {
        return properties.getLockoutSeconds();
    }
}
