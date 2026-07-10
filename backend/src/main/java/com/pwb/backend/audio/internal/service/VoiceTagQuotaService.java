package com.pwb.backend.audio.internal.service;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagQuotaService {

  private static final String ACTIVE_COUNT_PREFIX = "voice_tag:active_count:";
  private static final String STORAGE_BYTES_PREFIX = "voice_tag:storage_bytes:";
  private static final String DAILY_ZSET_PREFIX = "voice_tag:daily_zset:";

  private static final RedisScript<Long> INCR_LUA = new DefaultRedisScript<>(
      "local n = redis.call('INCR', KEYS[1])\n"
          + "if n == 1 then redis.call('EXPIRE', KEYS[1], 3600) end\n"
          + "if n > tonumber(ARGV[1]) then\n"
          + "  redis.call('DECR', KEYS[1])\n"
          + "  return -1\n"
          + "end\n"
          + "return n\n",
      Long.class
  );

  private final StringRedisTemplate redisTemplate;
  private final AudioProperties audioProperties;

  public void reserveActiveSlot(String userId) {
    String key = ACTIVE_COUNT_PREFIX + userId;
    int max = audioProperties.getVoiceTag().getMaxActive();
    Long result = redisTemplate.execute(INCR_LUA, List.of(key), String.valueOf(max));
    if (result == null || result == -1L) {
      throw new BusinessException(ErrorCode.VOICE_TAG_LIMIT_EXCEEDED,
          "Voice tag active cap reached");
    }
  }

  public void releaseActiveSlot(String userId) {
    String key = ACTIVE_COUNT_PREFIX + userId;
    redisTemplate.opsForValue().decrement(key);
  }

  public void trackStorage(String userId, long deltaBytes) {
    String key = STORAGE_BYTES_PREFIX + userId;
    Long total = redisTemplate.opsForValue().increment(key, deltaBytes);
    if (total != null && total == deltaBytes) {
      redisTemplate.expire(key, Duration.ofHours(1));
    }
    int max = audioProperties.getVoiceTag().getMaxStorageBytes();
    if (total != null && total > max) {
      redisTemplate.opsForValue().decrement(key, deltaBytes);
      throw new BusinessException(ErrorCode.VOICE_TAG_STORAGE_EXCEEDED,
          "Voice tag storage cap reached");
    }
  }

  public void releaseStorage(String userId, long deltaBytes) {
    String key = STORAGE_BYTES_PREFIX + userId;
    redisTemplate.opsForValue().decrement(key, deltaBytes);
  }

  public void trackDailyCreate(String userId) {
    String key = DAILY_ZSET_PREFIX + userId;
    long nowMs = System.currentTimeMillis();
    long cutoff = nowMs - Duration.ofHours(24).toMillis();
    String member = UUID.randomUUID().toString();
    redisTemplate.opsForZSet().add(key, member, nowMs);
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
    redisTemplate.expire(key, Duration.ofHours(25));
    Long count = redisTemplate.opsForZSet().count(key, cutoff, Double.POSITIVE_INFINITY);
    int quota = audioProperties.getVoiceTag().getDailyQuota();
    if (count != null && count > quota) {
      redisTemplate.opsForZSet().remove(key, member);
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
          "Daily voice tag creation quota exceeded");
    }
  }
}
