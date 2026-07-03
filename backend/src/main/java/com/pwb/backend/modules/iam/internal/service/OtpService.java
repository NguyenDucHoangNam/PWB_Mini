package com.pwb.backend.modules.iam.internal.service;

import com.pwb.backend.modules.iam.internal.config.IamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateOtp() {
    int otp = secureRandom.nextInt(900000) + 100000;
    return String.valueOf(otp);
  }

  public void storeOtp(String email, String otpCode) {
    long otpTtlSeconds = iamProperties.getOtp().getExpiration();
    long cooldownTtlSeconds = iamProperties.getOtp().getCooldown();

    byte[] otpKey = (OTP_KEY_PREFIX + email).getBytes();
    byte[] cooldownKey = (COOLDOWN_KEY_PREFIX + email).getBytes();
    byte[] otpValue = otpCode.getBytes();
    byte[] cooldownValue = "true".getBytes();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      connection.stringCommands().setEx(otpKey, otpTtlSeconds, otpValue);
      connection.stringCommands().setEx(cooldownKey, cooldownTtlSeconds, cooldownValue);
      return null;
    });
  }

  public void storeOtpWithAttemptsReset(String email, String otpCode) {
    long otpTtlSeconds = iamProperties.getOtp().getExpiration();
    long cooldownTtlSeconds = iamProperties.getOtp().getCooldown();

    byte[] attemptsKey = (ATTEMPTS_KEY_PREFIX + email).getBytes();
    byte[] otpKey = (OTP_KEY_PREFIX + email).getBytes();
    byte[] cooldownKey = (COOLDOWN_KEY_PREFIX + email).getBytes();
    byte[] otpValue = otpCode.getBytes();
    byte[] cooldownValue = "true".getBytes();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      connection.keyCommands().del(attemptsKey);
      connection.stringCommands().setEx(otpKey, otpTtlSeconds, otpValue);
      connection.stringCommands().setEx(cooldownKey, cooldownTtlSeconds, cooldownValue);
      return null;
    });
  }

  public String getStoredOtp(String email) {
    return redisTemplate.opsForValue().get(OTP_KEY_PREFIX + email);
  }

  public boolean checkCooldown(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email));
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
    return value != null ? Long.parseLong(value) : 0;
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
