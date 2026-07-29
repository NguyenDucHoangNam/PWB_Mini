package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.CooldownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCooldownService implements CooldownService {

    private static final String REGISTER_KEY_PREFIX = "iam:cooldown:register:";
    private static final String RESEND_KEY_PREFIX = "iam:cooldown:resend:";
    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    @Override
    public long enforceRegisterCooldown(String email) {
        String key = REGISTER_KEY_PREFIX + email.toLowerCase();
        return checkAndSet(key, DEFAULT_COOLDOWN);
    }

    @Override
    public long enforceResendOtpCooldown(String email) {
        String key = RESEND_KEY_PREFIX + email.toLowerCase();
        return checkAndSet(key, DEFAULT_COOLDOWN);
    }

    private long checkAndSet(String key, Duration cooldown) {
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
