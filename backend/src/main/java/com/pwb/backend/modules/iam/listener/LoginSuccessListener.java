package com.pwb.backend.modules.iam.listener;

import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.config.GeoIpConfig;
import com.pwb.backend.modules.iam.event.LoginSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSuccessListener {

    private static final long LAST_LOGIN_TTL_DAYS = 30;
    private static final String LAST_LOGIN_KEY_PREFIX = "user:last_login:";

    private final StringRedisTemplate redisTemplate;
    private final DatabaseReader geoIpDatabaseReader;

    @Async
    @EventListener
    public void handle(LoginSuccessEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        try {
            GeoIpConfig.GeoLocation location = GeoIpConfig.resolve(geoIpDatabaseReader, event.ip());
            Map<String, String> hash = new HashMap<>();
            hash.put("ip", event.ip() == null ? "unknown" : event.ip());
            hash.put("device", event.userAgent() == null ? "unknown" : event.userAgent());
            hash.put("country", location.country() == null ? "" : location.country());
            hash.put("city", location.city() == null ? "" : location.city());
            hash.put("timestamp", event.occurredAt() == null
                    ? Instant.now().toString()
                    : event.occurredAt().toString());

            String key = LAST_LOGIN_KEY_PREFIX + event.userId();
            redisTemplate.opsForHash().putAll(key, hash);
            redisTemplate.expire(key, java.time.Duration.ofDays(LAST_LOGIN_TTL_DAYS));

            if (isAnomalous(location, hash)) {
                log.warn("SUSPICIOUS_LOGIN_DETECTED userId={} ip={} location={}",
                        event.userId(), event.ip(), location.display());
            }
        } catch (Exception ex) {
            log.warn("Failed to process LoginSuccessEvent for {}: {}", event.userId(), ex.getMessage());
        }
    }

    private boolean isAnomalous(GeoIpConfig.GeoLocation location, Map<String, String> current) {
        if (location.country() == null || location.country().isBlank()) {
            return false;
        }
        return false;
    }

    public Duration lastLoginTtl() {
        return Duration.ofDays(LAST_LOGIN_TTL_DAYS);
    }
}
