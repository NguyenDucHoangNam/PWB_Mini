package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.audio.internal.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadClaimService {

  private static final String KEY_PREFIX = "demo:upload:claim:";

  private final StringRedisTemplate redisTemplate;
  private final AudioProperties audioProperties;

  public void put(String s3Key, String userId, long expectedSize, String expectedContentType) {
    String key = KEY_PREFIX + s3Key;
    Map<String, String> payload = new HashMap<>();
    payload.put("userId", userId);
    payload.put("expectedSizeBytes", String.valueOf(expectedSize));
    payload.put("expectedContentType", expectedContentType);
    payload.put("issuedAt", Instant.now().toString());
    redisTemplate.opsForHash().putAll(key, new HashMap<>(payload));
    redisTemplate.expire(key, Duration.ofSeconds(audioProperties.getUpload().getClaimRedisTtl()));
    log.info("Stored upload claim: key={}, userId={}, size={}", s3Key, userId, expectedSize);
  }

  public Optional<UploadClaim> get(String s3Key) {
    String key = KEY_PREFIX + s3Key;
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
    if (entries == null || entries.isEmpty()) {
      return Optional.empty();
    }
    String userId = (String) entries.get("userId");
    String sizeStr = (String) entries.get("expectedSizeBytes");
    String contentType = (String) entries.get("expectedContentType");
    String issuedStr = (String) entries.get("issuedAt");
    if (userId == null || sizeStr == null) {
      return Optional.empty();
    }
    long size = Long.parseLong(sizeStr);
    Instant issuedAt = issuedStr != null ? Instant.parse(issuedStr) : Instant.now();
    return Optional.of(new UploadClaim(userId, size, contentType, issuedAt));
  }

  public void delete(String s3Key) {
    String key = KEY_PREFIX + s3Key;
    redisTemplate.delete(key);
    log.info("Deleted upload claim: key={}", s3Key);
  }
}
