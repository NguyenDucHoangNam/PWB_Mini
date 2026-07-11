package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

  private static final String COOLDOWN_KEY_PREFIX = "demo:otp:cooldown:";
  private static final String DENIED_KEY_PREFIX = "demo:otp:denied:";
  private static final String ATTEMPTS_KEY_PREFIX = "demo:otp:attempts:";
  private static final String LOCKED_KEY_PREFIX = "demo:otp:locked:";
  private static final String CODE_HASH_KEY_PREFIX = "demo:otp:code:";

  private final AudioProperties audioProperties;
  private final StringRedisTemplate redisTemplate;
  private final SecureRandom secureRandom = new SecureRandom();

  public String issueCode(String shareToken) {
    String cooldownKey = COOLDOWN_KEY_PREFIX + shareToken;
    Boolean inCooldown = redisTemplate.hasKey(cooldownKey);
    if (Boolean.TRUE.equals(inCooldown)) {
      throw new BusinessException(ErrorCode.OTP_COOLDOWN,
          "Wait before requesting another OTP for this share token");
    }

    String lockedKey = LOCKED_KEY_PREFIX + shareToken;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockedKey))) {
      throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
          "OTP verification locked for this share token");
    }

    int length = audioProperties.getOtp().getLength();
    int max = (int) Math.pow(10, length);
    int min = (int) Math.pow(10, length - 1);
    int code = secureRandom.nextInt(max - min) + min;
    String codeStr = String.valueOf(code);

    String hash = hashCode(shareToken, codeStr);
    redisTemplate.opsForValue().set(CODE_HASH_KEY_PREFIX + shareToken, hash,
        Duration.ofSeconds(audioProperties.getOtp().getCodeTtlSeconds()));

    redisTemplate.opsForValue().set(cooldownKey, "1",
        Duration.ofSeconds(audioProperties.getOtp().getCooldownSeconds()));
    redisTemplate.delete(ATTEMPTS_KEY_PREFIX + shareToken);
    return codeStr;
  }

  public boolean verify(String shareToken, String submitted) {
    String lockedKey = LOCKED_KEY_PREFIX + shareToken;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockedKey))) {
      throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED, "OTP locked");
    }

    String expectedHash = redisTemplate.opsForValue().get(CODE_HASH_KEY_PREFIX + shareToken);
    if (expectedHash == null) {
      throw new BusinessException(ErrorCode.OTP_EXPIRED, "OTP expired or not issued");
    }

    String submittedHash = hashCode(shareToken, submitted);
    boolean matches = constantTimeEquals(expectedHash, submittedHash);

    if (!matches) {
      Long attempts = redisTemplate.opsForValue().increment(ATTEMPTS_KEY_PREFIX + shareToken);
      if (attempts != null && attempts == 1L) {
        redisTemplate.expire(ATTEMPTS_KEY_PREFIX + shareToken,
            Duration.ofSeconds(audioProperties.getOtp().getCodeTtlSeconds()));
      }
      if (attempts != null && attempts >= audioProperties.getOtp().getMaxAttempts()) {
        redisTemplate.opsForValue().set(lockedKey, "1",
            Duration.ofSeconds(audioProperties.getOtp().getLockSeconds()));
        redisTemplate.delete(CODE_HASH_KEY_PREFIX + shareToken);
        log.warn("OTP locked for shareToken={} after {} attempts", shareToken, attempts);
      }
      redisTemplate.opsForValue().set(DENIED_KEY_PREFIX + shareToken, "1",
          Duration.ofSeconds(audioProperties.getOtp().getCodeTtlSeconds()));
      throw new BusinessException(ErrorCode.INVALID_OTP, "Invalid OTP code");
    }

    redisTemplate.delete(CODE_HASH_KEY_PREFIX + shareToken);
    redisTemplate.delete(ATTEMPTS_KEY_PREFIX + shareToken);
    redisTemplate.delete(DENIED_KEY_PREFIX + shareToken);
    return true;
  }

  private String hashCode(String shareToken, String code) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(shareToken.getBytes(StandardCharsets.UTF_8));
      byte[] digest = md.digest(code.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 missing", e);
    }
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    if (a.length() != b.length()) return false;
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}