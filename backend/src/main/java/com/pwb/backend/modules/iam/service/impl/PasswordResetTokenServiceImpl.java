package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.modules.iam.service.PasswordResetTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private static final String KEY_PREFIX = "password_reset_token:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public PasswordResetTokenServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.iam.password-reset.token-ttl-seconds:600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public String issueToken(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, normalized, ttl);
        log.debug("Password reset token issued email={}", normalized);
        return token;
    }

    @Override
    public String consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(KEY_PREFIX + token);
    }

    @Override
    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + token);
    }

    @Override
    public Duration ttl() {
        return ttl;
    }
}
