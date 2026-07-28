package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.CooldownService;
import com.pwb.iam.infrastructure.security.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisCooldownService implements CooldownService {

    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";

    private final PasswordResetProperties passwordResetProperties;

    @Nullable
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public long enforceResetCooldown(String email) {
        if (stringRedisTemplate == null) {
            return 0L;
        }
        String key = COOLDOWN_PREFIX + email;
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            return ttl;
        }
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", Duration.ofSeconds(passwordResetProperties.getCooldownSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            Long remaining = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            return remaining != null && remaining > 0 ? remaining : passwordResetProperties.getCooldownSeconds();
        }
        return 0L;
    }
}
