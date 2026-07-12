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
    private static final String FIELD_IP = "ip";
    private static final String FIELD_DEVICE = "device";
    private static final String FIELD_COUNTRY = "country";
    private static final String FIELD_CITY = "city";
    private static final String FIELD_TIMESTAMP = "timestamp";

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
            Map<String, String> current = new HashMap<>();
            current.put(FIELD_IP, event.ip() == null ? "unknown" : event.ip());
            current.put(FIELD_DEVICE, event.userAgent() == null ? "unknown" : event.userAgent());
            current.put(FIELD_COUNTRY, location.country() == null ? "" : location.country());
            current.put(FIELD_CITY, location.city() == null ? "" : location.city());
            current.put(FIELD_TIMESTAMP, event.occurredAt() == null
                    ? Instant.now().toString()
                    : event.occurredAt().toString());

            String key = LAST_LOGIN_KEY_PREFIX + event.userId();
            Map<Object, Object> previous = redisTemplate.opsForHash().entries(key);
            redisTemplate.opsForHash().putAll(key, current);
            redisTemplate.expire(key, Duration.ofDays(LAST_LOGIN_TTL_DAYS));

            if (isAnomalous(location, current, previous)) {
                log.warn("SUSPICIOUS_LOGIN_DETECTED userId={} ip={} previousIp={} previousCountry={} currentCountry={}",
                        event.userId(), event.ip(),
                        previous.get(FIELD_IP), previous.get(FIELD_COUNTRY), location.country());
            }
        } catch (Exception ex) {
            log.warn("Failed to process LoginSuccessEvent for {}: {}", event.userId(), ex.getMessage());
        }
    }

    private boolean isAnomalous(GeoIpConfig.GeoLocation location, Map<String, String> current, Map<Object, Object> previous) {
        if (previous == null || previous.isEmpty()) {
            return false;
        }
        String previousIp = asString(previous.get(FIELD_IP));
        String previousCountry = asString(previous.get(FIELD_COUNTRY));
        String previousDevice = asString(previous.get(FIELD_DEVICE));

        boolean ipChanged = previousIp != null && !previousIp.isBlank()
                && !previousIp.equals(current.get(FIELD_IP));
        boolean countryChanged = previousCountry != null && !previousCountry.isBlank()
                && location.country() != null
                && !previousCountry.equalsIgnoreCase(location.country());
        boolean deviceChanged = previousDevice != null && !previousDevice.isBlank()
                && !previousDevice.equals(current.get(FIELD_DEVICE));

        return (ipChanged || countryChanged) && deviceChanged;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    public Duration lastLoginTtl() {
        return Duration.ofDays(LAST_LOGIN_TTL_DAYS);
    }
}
