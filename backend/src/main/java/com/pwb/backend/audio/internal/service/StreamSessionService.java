package com.pwb.backend.audio.internal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamSessionService {

  private final StringRedisTemplate redisTemplate;

  private static String activeSetKey(String shareToken) {
    return "demo:distribution:active_sessions:" + shareToken;
  }

  private static String jtiRevokedKey(String jti) {
    return "stream:cookie:revoked:" + jti;
  }

  public void registerJti(String shareToken, String jti, Duration ttl) {
    String key = activeSetKey(shareToken);
    redisTemplate.opsForSet().add(key, jti);
    redisTemplate.expire(key, ttl.plusMinutes(5));
  }

  public Set<String> getActiveJtis(String shareToken) {
    String key = activeSetKey(shareToken);
    Set<String> members = redisTemplate.opsForSet().members(key);
    return members == null ? Set.of() : new HashSet<>(members);
  }

  public void revokeJti(String jti, Duration ttl) {
    if (jti == null || jti.isBlank()) {
      return;
    }
    redisTemplate.opsForValue().set(jtiRevokedKey(jti), "1", ttl);
  }

  public boolean isJtiRevoked(String jti) {
    if (jti == null) {
      return false;
    }
    Boolean exists = redisTemplate.hasKey(jtiRevokedKey(jti));
    return Boolean.TRUE.equals(exists);
  }

  public int blacklistAllJtisForShareToken(String shareToken, Duration ttl) {
    Set<String> jtis = getActiveJtis(shareToken);
    for (String jti : jtis) {
      revokeJti(jti, ttl);
    }
    redisTemplate.delete(activeSetKey(shareToken));
    log.info("JTI_BULK_BLACKLISTED shareToken={} count={}", shareToken, jtis.size());
    return jtis.size();
  }

  public boolean incrementKeysRequestCount(String shareToken, int max) {
    String key = "stream:keys_request_count:" + shareToken;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofHours(1));
    }
    return count == null || count <= max;
  }
}