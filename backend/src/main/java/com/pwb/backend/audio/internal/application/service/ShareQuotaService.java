package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareQuotaService {

  private static final String RECIPIENTS_PREFIX = "share:daily_recipients:";
  private static final String COUNT_PREFIX = "share:daily_count:";
  private static final Duration WINDOW = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;
  private final AudioProperties audioProperties;

  public void checkAndIncrement(String producerId, String recipientEmail) {
    int countQuota = audioProperties.getDistribution().getDailyCountQuota();
    int recipientsQuota = audioProperties.getDistribution().getDailyRecipientsQuota();

    String countKey = COUNT_PREFIX + producerId;
    Long count = redisTemplate.opsForValue().increment(countKey);
    if (count != null && count == 1L) {
      redisTemplate.expire(countKey, WINDOW);
    }
    if (count != null && count > countQuota) {
      redisTemplate.opsForValue().decrement(countKey);
      log.warn("SHARE_QUOTA_EXCEEDED producerId={} type=count quota={}", producerId, countQuota);
      throw new BusinessException(ErrorCode.SHARE_QUOTA_EXCEEDED,
          "Daily distribution count quota exceeded");
    }

    String dailyRecipientsKey = RECIPIENTS_PREFIX + producerId + ":" + currentDay();
    Long added = redisTemplate.opsForSet().add(dailyRecipientsKey, recipientEmail);
    if (added != null && added > 0L) {
      redisTemplate.expire(dailyRecipientsKey, Duration.ofSeconds(TimeUnit.DAYS.toSeconds(2)));
    }
    Long recipientsSize = redisTemplate.opsForSet().size(dailyRecipientsKey);
    if (recipientsSize != null && recipientsSize > recipientsQuota) {
      redisTemplate.opsForSet().remove(dailyRecipientsKey, recipientEmail);
      log.warn("SHARE_QUOTA_EXCEEDED producerId={} type=recipients quota={}", producerId, recipientsQuota);
      throw new BusinessException(ErrorCode.SHARE_QUOTA_EXCEEDED,
          "Daily unique recipients quota exceeded");
    }
  }

  private long currentDay() {
    return System.currentTimeMillis() / WINDOW.toMillis();
  }
}
