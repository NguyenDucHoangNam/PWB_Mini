package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    byte[] otpValue = hashOtp(email, otpCode).getBytes();
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
    byte[] otpValue = hashOtp(email, otpCode).getBytes();
    byte[] cooldownValue = "true".getBytes();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      connection.keyCommands().del(attemptsKey);
      connection.stringCommands().setEx(otpKey, otpTtlSeconds, otpValue);
      connection.stringCommands().setEx(cooldownKey, cooldownTtlSeconds, cooldownValue);
      return null;
    });
  }

  public boolean verifyOtp(String email, String submittedOtp) {
    String storedHash = redisTemplate.opsForValue().get(OTP_KEY_PREFIX + email);
    if (storedHash == null) {
      return false;
    }
    return constantTimeEquals(storedHash, hashOtp(email, submittedOtp));
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

  private String hashOtp(String email, String otpCode) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
      byte[] hash = digest.digest(otpCode.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }
}
