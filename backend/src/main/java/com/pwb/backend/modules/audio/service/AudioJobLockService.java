package com.pwb.backend.modules.audio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioJobLockService {

    private static final String KEY_PREFIX = "audio:job:lock:";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        RELEASE_SCRIPT = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public AcquiredLock tryAcquire(UUID demoId, Duration ttl) {
        String key = KEY_PREFIX + demoId;
        String token = UUID.randomUUID().toString();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        boolean acquired = Boolean.TRUE.equals(result);
        if (acquired) {
            log.debug("AUDIO_JOB_LOCK_ACQUIRED demoId={} ttlSeconds={}", demoId, ttl.toSeconds());
            return new AcquiredLock(key, token);
        }
        log.debug("AUDIO_JOB_LOCK_DENIED demoId={}", demoId);
        return null;
    }

    public void release(AcquiredLock lock) {
        if (lock == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lock.key()), lock.token());
        } catch (Exception ex) {
            log.warn("AUDIO_JOB_LOCK_RELEASE_FAILED key={} reason={}", lock.key(), ex.getMessage());
        }
        log.debug("AUDIO_JOB_LOCK_RELEASED key={}", lock.key());
    }

    public record AcquiredLock(String key, String token) {
    }
}