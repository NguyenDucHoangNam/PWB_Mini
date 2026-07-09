package com.pwb.backend.iam.internal.helper;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginLockoutHelper {

  private final IamProperties iamProperties;

  public int getMaxAttempts() {
    return iamProperties.getLogin().getLockout().getMaxAttempts();
  }

  public Duration getWindowDuration() {
    return Duration.ofMinutes(iamProperties.getLogin().getLockout().getWindowDurationMinutes());
  }

  public boolean isLocked(StringRedisTemplate redisTemplate, String userId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey(userId)));
  }

  public void ensureNotLocked(StringRedisTemplate redisTemplate, String userId) {
    if (isLocked(redisTemplate, userId)) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
          "Account is temporarily locked, please try again later");
    }
  }

  public void recordFailure(StringRedisTemplate redisTemplate, String userId) {
    String attemptsKey = "login_attempts:" + userId;
    Duration windowDuration = getWindowDuration();
    int maxAttempts = getMaxAttempts();

    Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
    if (attempts != null && attempts == 1) {
      redisTemplate.expire(attemptsKey, windowDuration);
    }
    if (attempts != null && attempts >= maxAttempts) {
      redisTemplate.opsForValue().set(lockoutKey(userId), "true", windowDuration);
      redisTemplate.delete(attemptsKey);
      throw new BusinessException(
          ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
          "Account is temporarily locked, please try again later");
    }
  }

  /**
   * Records a failure against an arbitrary bucket key with the given limits.
   * Used by per-email / per-IP rate limits where the bucketed key is not
   * a userId.
   *
   * @return true if the bucket just reached the max attempts and was locked.
   */
  public boolean recordFailure(StringRedisTemplate redisTemplate, String attemptsKey,
                                String lockoutKey, int maxAttempts, Duration window) {
    Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
    if (attempts != null && attempts == 1) {
      redisTemplate.expire(attemptsKey, window);
    }
    if (attempts != null && attempts >= maxAttempts) {
      redisTemplate.opsForValue().set(lockoutKey, "true", window);
      redisTemplate.delete(attemptsKey);
      return true;
    }
    return false;
  }

  public boolean isLockedKey(StringRedisTemplate redisTemplate, String lockoutKey) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey));
  }

  public void ensureNotLockedKey(StringRedisTemplate redisTemplate, String lockoutKey) {
    if (isLockedKey(redisTemplate, lockoutKey)) {
      throw new BusinessException(
          ErrorCode.RATE_LIMIT_EXCEEDED,
          "Too many requests, please try again later");
    }
  }

  public void clear(StringRedisTemplate redisTemplate, String userId) {
    redisTemplate.delete("login_attempts:" + userId);
    redisTemplate.delete(lockoutKey(userId));
  }

  public static String lockoutKey(String userId) {
    return "login_lockout:" + userId;
  }

  public static String attemptsKey(String userId) {
    return "login_attempts:" + userId;
  }
}