package com.pwb.backend.modules.iam.listener;

import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.config.GeoIpConfig;
import com.pwb.backend.common.outbox.OutboxService;
import com.pwb.backend.common.outbox.event.SuspiciousLoginEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.modules.iam.event.LoginSuccessEvent;
import com.pwb.backend.modules.iam.repository.UserRepository;
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
import java.util.Optional;

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
    private static final String UNKNOWN_IP = "unknown";

    private final StringRedisTemplate redisTemplate;
    private final DatabaseReader geoIpDatabaseReader;
    private final OutboxService outboxService;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void handle(LoginSuccessEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        try {
            GeoIpConfig.GeoLocation location = GeoIpConfig.resolve(geoIpDatabaseReader, event.ip());
            String resolvedIp = event.ip() == null ? UNKNOWN_IP : event.ip();
            Map<String, String> current = new HashMap<>();
            current.put(FIELD_IP, resolvedIp);
            current.put(FIELD_DEVICE, event.userAgent() == null ? UNKNOWN_IP : event.userAgent());
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
                publishSuspiciousLoginAlert(event, location, current, previous);
            }
        } catch (Exception ex) {
            log.warn("Failed to process LoginSuccessEvent for {}: {}", event.userId(), ex.getMessage());
        }
    }

    private void publishSuspiciousLoginAlert(LoginSuccessEvent event,
                                             GeoIpConfig.GeoLocation location,
                                             Map<String, String> current,
                                             Map<Object, Object> previous) {
        if (UNKNOWN_IP.equalsIgnoreCase(current.get(FIELD_IP))) {
            return;
        }
        String fullName = userRepository.findById(event.userId())
                .map(u -> u.getFullName() == null ? "" : u.getFullName())
                .orElse("");
        Optional<Map<Object, Object>> previousSafe = Optional.ofNullable(previous);
        SuspiciousLoginEvent payload = new SuspiciousLoginEvent(
                event.userId(),
                event.email() == null ? "" : event.email(),
                fullName,
                current.get(FIELD_IP),
                previousSafe.map(p -> asString(p.get(FIELD_IP))).orElse(null),
                current.get(FIELD_COUNTRY),
                previousSafe.map(p -> asString(p.get(FIELD_COUNTRY))).orElse(null),
                current.get(FIELD_DEVICE),
                previousSafe.map(p -> asString(p.get(FIELD_DEVICE))).orElse(null),
                current.get(FIELD_CITY),
                Instant.now());
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_USER,
                event.userId(),
                OutboxEventTypes.SUSPICIOUS_LOGIN,
                event.userId().toString(),
                payload);
        log.info("SUSPICIOUS_LOGIN_ALERT_PUBLISHED userId={} email={}",
                event.userId(), MaskingLogArg.email(event.email()));
    }

    private boolean isAnomalous(GeoIpConfig.GeoLocation location, Map<String, String> current, Map<Object, Object> previous) {
        if (previous == null || previous.isEmpty()) {
            return false;
        }
        String currentIp = current.get(FIELD_IP);
        if (currentIp == null || currentIp.isBlank() || UNKNOWN_IP.equalsIgnoreCase(currentIp)) {
            return false;
        }
        String previousIp = asString(previous.get(FIELD_IP));
        String previousCountry = asString(previous.get(FIELD_COUNTRY));
        String previousDevice = asString(previous.get(FIELD_DEVICE));

        boolean ipChanged = previousIp != null && !previousIp.isBlank()
                && !previousIp.equals(currentIp);
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