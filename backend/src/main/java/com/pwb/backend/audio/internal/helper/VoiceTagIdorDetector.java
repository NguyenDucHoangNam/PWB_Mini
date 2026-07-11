package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.shared.web.security.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceTagIdorDetector {

  private static final String IDOR_FAIL_PREFIX = "voice_tag:idor_fail:";
  private static final String IDOR_BLOCK_PREFIX = "voice_tag:idor_blocked:";

  private final StringRedisTemplate redisTemplate;
  private final ClientIpResolver clientIpResolver;
  private final AudioProperties audioProperties;

  public void recordAttempt(String ip) {
    if (ip == null || ip.isBlank() || "Unknown".equals(ip)) {
      return;
    }
    String key = IDOR_FAIL_PREFIX + ip;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(
          audioProperties.getDistribution().getIdorWarnWindowSeconds()));
    }
    int threshold = audioProperties.getDistribution().getIdorWarnThreshold();
    if (count != null && count >= threshold) {
      String blockKey = IDOR_BLOCK_PREFIX + ip;
      redisTemplate.opsForValue().set(blockKey, "1",
          Duration.ofSeconds(audioProperties.getDistribution().getIdorBlockSeconds()));
      log.warn("IDOR block triggered for ip={} count={}", ip, count);
    }
  }

  public boolean isBlocked(String ip) {
    if (ip == null || ip.isBlank()) return false;
    return Boolean.TRUE.equals(redisTemplate.hasKey(IDOR_BLOCK_PREFIX + ip));
  }

  public void record(String tagId, String requesterUserId) {
    String ip = clientIpResolver.current();
    log.warn("IDOR_ATTEMPT tagId={} requesterUserId={} ip={}", tagId, requesterUserId, ip);
    recordAttempt(ip);
  }
}
