package com.pwb.backend.modules.audio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadClaimService {

    private static final String KEY_PREFIX = "demo:upload:claim:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String s3Key, UUID userId, String contentType, long fileSize, Duration ttl) {
        String hashKey = KEY_PREFIX + s3Key;
        stringRedisTemplate.opsForHash().put(hashKey, "userId", userId.toString());
        stringRedisTemplate.opsForHash().put(hashKey, "contentType", contentType);
        stringRedisTemplate.opsForHash().put(hashKey, "fileSize", Long.toString(fileSize));
        stringRedisTemplate.opsForHash().put(hashKey, "issuedAt", Long.toString(System.currentTimeMillis()));
        stringRedisTemplate.expire(hashKey, ttl);
        log.debug("UPLOAD_CLAIM_SAVED s3Key={} userId={} ttlSeconds={}", s3Key, userId, ttl.toSeconds());
    }

    public UploadClaim load(String s3Key) {
        String hashKey = KEY_PREFIX + s3Key;
        Object userId = stringRedisTemplate.opsForHash().get(hashKey, "userId");
        Object contentType = stringRedisTemplate.opsForHash().get(hashKey, "contentType");
        Object fileSize = stringRedisTemplate.opsForHash().get(hashKey, "fileSize");
        if (userId == null || contentType == null || fileSize == null) {
            return null;
        }
        try {
            return new UploadClaim(
                    UUID.fromString(userId.toString()),
                    contentType.toString(),
                    Long.parseLong(fileSize.toString()));
        } catch (IllegalArgumentException ex) {
            log.warn("UPLOAD_CLAIM_CORRUPTED s3Key={} reason={}", s3Key, ex.getMessage());
            return null;
        }
    }

    public void delete(String s3Key) {
        String hashKey = KEY_PREFIX + s3Key;
        stringRedisTemplate.delete(hashKey);
        log.debug("UPLOAD_CLAIM_DELETED s3Key={}", s3Key);
    }

    public record UploadClaim(UUID userId, String contentType, long fileSize) {
    }
}