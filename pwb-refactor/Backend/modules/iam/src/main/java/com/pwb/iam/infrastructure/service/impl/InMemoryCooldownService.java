package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.CooldownService;
import com.pwb.iam.infrastructure.config.OtpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InMemoryCooldownService implements CooldownService {

    private final ConcurrentHashMap<String, Long> lastRegisterAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastResendAt = new ConcurrentHashMap<>();
    private final OtpProperties properties;

    public InMemoryCooldownService(OtpProperties properties) {
        this.properties = properties;
    }

    @Override
    public long enforceRegisterCooldown(String email) {
        long now = System.currentTimeMillis();
        long last = lastRegisterAt.getOrDefault(email, 0L);
        long elapsed = now - last;
        long cooldownMs = Duration.ofSeconds(properties.getResendCooldownSeconds()).toMillis();
        if (elapsed < cooldownMs) {
            long remaining = (cooldownMs - elapsed) / 1000L;
            log.debug("Register cooldown active: email={} remaining={}s", email, remaining);
            return remaining;
        }
        lastRegisterAt.put(email, now);
        return 0L;
    }

    @Override
    public long enforceResendOtpCooldown(String email) {
        long now = System.currentTimeMillis();
        long last = lastResendAt.getOrDefault(email, 0L);
        long elapsed = now - last;
        long cooldownMs = Duration.ofSeconds(properties.getResendCooldownSeconds()).toMillis();
        if (elapsed < cooldownMs) {
            long remaining = (cooldownMs - elapsed) / 1000L;
            return remaining;
        }
        lastResendAt.put(email, now);
        return 0L;
    }
}
