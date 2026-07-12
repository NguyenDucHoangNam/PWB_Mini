package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.audio.constant.AudioRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamKeyCacheService {

    public static final int CURRENT_VERSION = 1;

    private static final String FIELD_KEY_BYTES = "keyBytes";
    private static final String FIELD_VERSION = "version";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    public Optional<byte[]> getKeyBytes(UUID demoId) {
        if (demoId == null) {
            return Optional.empty();
        }
        try {
            HashOperations<String, String, String> ops = stringRedisTemplate.opsForHash();
            String key = AudioRedisKeys.demoKeyCacheKey(demoId);
            String base64 = ops.get(key, FIELD_KEY_BYTES);
            String version = ops.get(key, FIELD_VERSION);
            if (base64 == null || version == null) {
                return Optional.empty();
            }
            byte[] decoded = Base64.getDecoder().decode(base64);
            return Optional.of(decoded);
        } catch (Exception ex) {
            log.warn("STREAM_KEY_CACHE_GET_FAILED demoId={} reason={}", demoId, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID demoId, byte[] keyBytes) {
        if (demoId == null || keyBytes == null) {
            return;
        }
        try {
            String key = AudioRedisKeys.demoKeyCacheKey(demoId);
            String base64 = Base64.getEncoder().encodeToString(keyBytes);
            Map<String, String> values = Map.of(
                    FIELD_KEY_BYTES, base64,
                    FIELD_VERSION, String.valueOf(CURRENT_VERSION));
            stringRedisTemplate.opsForHash().putAll(key, values);
            stringRedisTemplate.expire(key, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("STREAM_KEY_CACHE_SET_FAILED demoId={} reason={}", demoId, ex.getMessage());
        }
    }

    public void evict(UUID demoId) {
        if (demoId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(AudioRedisKeys.demoKeyCacheKey(demoId));
        } catch (Exception ex) {
            log.warn("STREAM_KEY_CACHE_EVICT_FAILED demoId={} reason={}", demoId, ex.getMessage());
        }
    }
}
