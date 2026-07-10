package com.pwb.backend.audio.internal.service;

import com.pwb.backend.audio.internal.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class BruteForceGuardService {

  private final StringRedisTemplate redisTemplate;
  private final AudioProperties audioProperties;

  private static String failCountKey(String ip) {
    return "share_token:fail_count:" + ip;
  }

  private static String ipBlockedKey(String ip) {
    return "share_token:ip_blocked:" + ip;
  }

  public int recordFailure(String ip) {
    if (ip == null || ip.isBlank()) {
      return 0;
    }
    String key = failCountKey(ip);
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofMinutes(5));
    }
    if (count != null && count > 50) {
      redisTemplate.opsForValue().set(ipBlockedKey(ip), "1", Duration.ofMinutes(15));
      log.warn("IP blocked by brute-force guard: ip={} count={}", ip, count);
    }
    return count == null ? 0 : count.intValue();
  }

  public boolean isIpBlocked(String ip) {
    if (ip == null) {
      return false;
    }
    Boolean exists = redisTemplate.hasKey(ipBlockedKey(ip));
    return Boolean.TRUE.equals(exists);
  }

  public void resetFailures(String ip) {
    if (ip == null) {
      return;
    }
    redisTemplate.delete(failCountKey(ip));
  }
}