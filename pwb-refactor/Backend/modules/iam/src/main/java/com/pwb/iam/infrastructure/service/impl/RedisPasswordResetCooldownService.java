package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.infrastructure.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPasswordResetCooldownService {

    private static final String KEY_PREFIX = "iam:password-reset:cooldown:";

    private final StringRedisTemplate redis;
    private final PasswordResetProperties properties;

    public long enforce(String email) {
        if (email == null || email.isBlank()) {
            return 0L;
        }
        String key = KEY_PREFIX + email.toLowerCase();
        try {
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl > 0) {
                return ttl;
            }
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    key, "1", Duration.ofSeconds(properties.getCooldownSeconds()));
            if (Boolean.FALSE.equals(acquired)) {
                Long remaining = redis.getExpire(key);
                return remaining != null && remaining > 0 ? remaining : properties.getCooldownSeconds();
            }
            return 0L;
        } catch (Exception ex) {
            log.warn("Redis unavailable for cooldown, fail-open: email={} reason={}", email, ex.getMessage());
            return 0L;
        }
    }
}