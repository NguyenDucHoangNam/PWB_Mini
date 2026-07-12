package com.pwb.backend.modules.audio.cache;

import com.pwb.backend.modules.audio.constant.AudioRedisKeys;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoStatusCache {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    public void put(UUID demoId, DemoStatus status) {
        if (demoId == null || status == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    AudioRedisKeys.demoStatusCacheKey(demoId),
                    status.name(),
                    CACHE_TTL);
        } catch (Exception ex) {
            log.warn("DEMO_STATUS_CACHE_SET_FAILED demoId={} reason={}", demoId, ex.getMessage());
        }
    }

    public DemoStatus get(UUID demoId) {
        if (demoId == null) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(AudioRedisKeys.demoStatusCacheKey(demoId));
            if (value == null) {
                return null;
            }
            return DemoStatus.valueOf(value);
        } catch (Exception ex) {
            log.warn("DEMO_STATUS_CACHE_GET_FAILED demoId={} reason={}", demoId, ex.getMessage());
            return null;
        }
    }

    public void evict(UUID demoId) {
        if (demoId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(AudioRedisKeys.demoStatusCacheKey(demoId));
        } catch (Exception ex) {
            log.warn("DEMO_STATUS_CACHE_EVICT_FAILED demoId={} reason={}", demoId, ex.getMessage());
        }
    }
}
