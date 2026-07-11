package com.pwb.backend.iam.internal.application.service;

import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpService {

  private static final String OTP_KEY_PREFIX = "otp:registration:";
  private static final String COOLDOWN_KEY_PREFIX = "otp:cooldown:";
  private static final String ATTEMPTS_KEY_PREFIX = "otp:attempts:";

  private final StringRedisTemplate redisTemplate;
  private final IamProperties iamProperties;
  private final RedisScript<Long> otpVerifyScript;
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateOtp() {
    int otp = secureRandom.nextInt(900000) + 100000;
    return String.valueOf(otp);
  }

  public void storeOtp(String email, String otpCode) {
    long otpTtlSeconds = iamProperties.getOtp().getExpiration();
    long cooldownTtlSeconds = iamProperties.getOtp().getCooldown();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      connection.stringCommands().setEx(
          (OTP_KEY_PREFIX + email).getBytes(),
          otpTtlSeconds,
          otpCode.getBytes());
      connection.stringCommands().setEx(
          (COOLDOWN_KEY_PREFIX + email).getBytes(),
          cooldownTtlSeconds,
          "true".getBytes());
      return null;
    });
  }

  public void storeOtpWithAttemptsReset(String email, String otpCode) {
    long otpTtlSeconds = iamProperties.getOtp().getExpiration();
    long cooldownTtlSeconds = iamProperties.getOtp().getCooldown();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      connection.keyCommands().del((ATTEMPTS_KEY_PREFIX + email).getBytes());
      connection.stringCommands().setEx(
          (OTP_KEY_PREFIX + email).getBytes(),
          otpTtlSeconds,
          otpCode.getBytes());
      connection.stringCommands().setEx(
          (COOLDOWN_KEY_PREFIX + email).getBytes(),
          cooldownTtlSeconds,
          "true".getBytes());
      return null;
    });
  }

  public boolean verifyOtp(String email, String submittedOtp) {
    if (submittedOtp == null) {
      return false;
    }
    Long result = redisTemplate.execute(
        otpVerifyScript,
        List.of(
            OTP_KEY_PREFIX + email,
            COOLDOWN_KEY_PREFIX + email,
            ATTEMPTS_KEY_PREFIX + email),
        submittedOtp);
    return result != null && result == 1L;
  }

  public String getStoredOtpHash(String email) {
    return redisTemplate.opsForValue().get(OTP_KEY_PREFIX + email);
  }

  public boolean checkCooldown(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email));
  }

  public boolean tryAcquireCooldown(String email, Duration ttl) {
    Boolean ok = redisTemplate.opsForValue()
        .setIfAbsent(COOLDOWN_KEY_PREFIX + email, "true", ttl);
    return Boolean.TRUE.equals(ok);
  }

  public long incrementAttempts(String email) {
    String key = ATTEMPTS_KEY_PREFIX + email;
    Long attempts = redisTemplate.opsForValue().increment(key);
    if (attempts != null && attempts == 1) {
      redisTemplate.expire(key, Duration.ofSeconds(iamProperties.getOtp().getExpiration()));
    }
    return attempts != null ? attempts : 0;
  }

  public long getAttempts(String email) {
    String value = redisTemplate.opsForValue().get(ATTEMPTS_KEY_PREFIX + email);
    if (value == null) {
      return 0;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public void deleteAllOtpKeys(String email) {
    redisTemplate.delete(List.of(
        OTP_KEY_PREFIX + email,
        COOLDOWN_KEY_PREFIX + email,
        ATTEMPTS_KEY_PREFIX + email
    ));
  }

  public void deleteOtpAndAttempts(String email) {
    redisTemplate.delete(List.of(
        OTP_KEY_PREFIX + email,
        ATTEMPTS_KEY_PREFIX + email
    ));
  }
}
