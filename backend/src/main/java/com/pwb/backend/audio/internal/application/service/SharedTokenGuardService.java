package com.pwb.backend.audio.internal.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedTokenGuardService {

  private static final int LOCK_TTL_SECONDS = 900;

  private final StringRedisTemplate redisTemplate;

  private static String lockKey(String shareToken) {
    return "demo:distribution:locked:" + shareToken;
  }

  public int incrementHit(String shareToken, int windowSeconds) {
    String key = "demo:distribution:hits:" + shareToken;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    if (count != null && count > 120) {
      lockShareToken(shareToken);
    }
    return count == null ? 0 : count.intValue();
  }

  public void lockShareToken(String shareToken) {
    redisTemplate.opsForValue().set(lockKey(shareToken), "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
    log.warn("SHARE_TOKEN_BRUTE_FORCE token={}", shareToken);
  }

  public boolean isLocked(String shareToken) {
    if (shareToken == null) {
      return false;
    }
    Boolean exists = redisTemplate.hasKey(lockKey(shareToken));
    return Boolean.TRUE.equals(exists);
  }

  public void unlock(String shareToken) {
    if (shareToken == null) {
      return;
    }
    redisTemplate.delete(lockKey(shareToken));
  }
}