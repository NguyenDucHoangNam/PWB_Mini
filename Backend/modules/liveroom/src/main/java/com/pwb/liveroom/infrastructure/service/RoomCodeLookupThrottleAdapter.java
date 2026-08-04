package com.pwb.liveroom.infrastructure.service;

import com.pwb.liveroom.domain.service.RoomCodeLookupThrottle;
import com.pwb.liveroom.infrastructure.config.properties.LiveroomConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCodeLookupThrottleAdapter implements RoomCodeLookupThrottle {

    private static final String KEY_PREFIX = "liveroom:code-lookup:";

    private final StringRedisTemplate redis;
    private final LiveroomConfig config;

    @Override
    public boolean tryConsume(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        int maxAttempts = config.getCodeLookup().getMaxAttempts();
        Duration window = config.getCodeLookup().getWindow();
        String key = KEY_PREFIX + clientIp;

        try {
            Long attempts = redis.opsForValue().increment(key);
            if (attempts == null) {
                return true;
            }
            if (attempts == 1L) {
                redis.expire(key, window);
            }
            if (attempts > maxAttempts) {
                log.warn("Room code lookup throttled: clientIp={} attempts={} limit={}",
                        clientIp, attempts, maxAttempts);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("Redis unavailable for room code lookup throttle: clientIp={} reason={}",
                    clientIp, ex.getMessage());
            return true;
        }
    }
}