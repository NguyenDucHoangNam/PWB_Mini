package com.pwb.backend.audio.internal.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.model.DemoDistribution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionCacheService {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final AudioProperties audioProperties;

  private static String distributionKey(String shareToken) {
    return "demo:distribution:" + shareToken;
  }

  private static String revokedKey(String shareToken) {
    return "demo:distribution:revoked:" + shareToken;
  }

  public CachedDistribution get(String shareToken) {
    String json = redisTemplate.opsForValue().get(distributionKey(shareToken));
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, CachedDistribution.class);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse distribution cache for token={}", shareToken);
      return null;
    }
  }

  public void put(DemoDistribution distribution) {
    if (distribution == null || distribution.getShareToken() == null) {
      return;
    }
    CachedDistribution cached = CachedDistribution.from(distribution);
    try {
      String json = objectMapper.writeValueAsString(cached);
      Duration ttl = Duration.ofSeconds(audioProperties.getDistribution().getShareLinkBaseUrlTtlSeconds());
      redisTemplate.opsForValue().set(distributionKey(distribution.getShareToken().toString()), json, ttl);
    } catch (JsonProcessingException e) {
      log.warn("Failed to cache distribution token={}", distribution.getShareToken());
    }
  }

  public void evict(String shareToken) {
    if (shareToken == null) {
      return;
    }
    redisTemplate.delete(distributionKey(shareToken));
  }

  public boolean isRevoked(String shareToken) {
    Boolean exists = redisTemplate.hasKey(revokedKey(shareToken));
    return Boolean.TRUE.equals(exists);
  }

  public void markRevoked(String shareToken) {
    if (shareToken == null) {
      return;
    }
    int ttl = audioProperties.getRevoke().getBlacklistTtlSeconds();
    redisTemplate.opsForValue().set(revokedKey(shareToken), "true", Duration.ofSeconds(ttl));
  }

  public void evictRevokedMarker(String shareToken) {
    if (shareToken == null) {
      return;
    }
    redisTemplate.delete(revokedKey(shareToken));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CachedDistribution(
      String distributionId,
      String threadId,
      String demoId,
      String recipientEmail,
      boolean allowDownload,
      boolean isRevoked,
      String demoStatus,
      Instant createdAt) {

    public static CachedDistribution from(DemoDistribution d) {
      return new CachedDistribution(
          d.getId(),
          d.getThreadId(),
          d.getDemoId(),
          d.getRecipientEmail(),
          d.isAllowDownload(),
          d.isRevoked(),
          null,
          d.getCreatedAt());
    }
  }
}