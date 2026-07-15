package com.pwb.backend.service.impl;

import com.pwb.backend.config.OtpProperties;
import com.pwb.backend.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String KEY_PREFIX = "otp:";
    private static final String COOLDOWN_PREFIX = "otp:last-sent:";
    private static final String DAILY_COUNT_PREFIX = "otp:daily-count:";
    private static final String DELIMITER = ":";
    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);

    private static final RedisScript<Long> DAILY_INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]);"
            + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end;"
            + "return current;",
            Long.class);

    private final OtpProperties otpProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    public String generateAndStore(UUID userId, String purpose) {
        String code = generateNumericCode(otpProperties.getLength());
        String hash = sha256(code);
        String hashKey = buildHashKey(userId, purpose);
        String attemptsKey = hashKey + ATTEMPTS_SUFFIX;

        redisTemplate.opsForValue().set(hashKey, hash, Duration.ofSeconds(otpProperties.getTtlSeconds()));
        redisTemplate.opsForValue().set(attemptsKey, "0", Duration.ofSeconds(otpProperties.getTtlSeconds()));
        redisTemplate.opsForValue().set(
                COOLDOWN_PREFIX + userId + DELIMITER + purpose,
                "1",
                Duration.ofSeconds(otpProperties.getResendCooldownSeconds()));

        String dailyKey = DAILY_COUNT_PREFIX + userId + DELIMITER + purpose;
        Long currentCount = redisTemplate.execute(
                DAILY_INCREMENT_SCRIPT,
                List.of(dailyKey),
                String.valueOf(DAILY_WINDOW.toSeconds()));
        if (currentCount == null) {
            redisTemplate.opsForValue().set(dailyKey, "1", DAILY_WINDOW);
        }

        return code;
    }

    @Override
    public VerificationResult verify(UUID userId, String purpose, String code) {
        String hashKey = buildHashKey(userId, purpose);
        String attemptsKey = hashKey + ATTEMPTS_SUFFIX;

        String storedHash = redisTemplate.opsForValue().get(hashKey);
        if (storedHash == null || storedHash.isBlank()) {
            return VerificationResult.EXPIRED_OR_MISSING;
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        long currentAttempts = attempts == null ? 1L : attempts;

        if (currentAttempts > otpProperties.getMaxAttempts()) {
            invalidate(userId, purpose);
            return VerificationResult.LOCKED;
        }

        boolean matched = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                sha256(code).getBytes(StandardCharsets.UTF_8));

        if (!matched) {
            if (currentAttempts >= otpProperties.getMaxAttempts()) {
                invalidate(userId, purpose);
                return VerificationResult.LOCKED;
            }
            return VerificationResult.INVALID;
        }

        invalidate(userId, purpose);
        return VerificationResult.OK;
    }

    @Override
    public void invalidate(UUID userId, String purpose) {
        String hashKey = buildHashKey(userId, purpose);
        redisTemplate.delete(hashKey);
        redisTemplate.delete(hashKey + ATTEMPTS_SUFFIX);
    }

    @Override
    public Duration resendCooldownRemaining(UUID userId, String purpose) {
        String cooldownKey = COOLDOWN_PREFIX + userId + DELIMITER + purpose;
        Long ttl = redisTemplate.getExpire(cooldownKey);
        if (ttl == null || ttl <= 0L) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(ttl);
    }

    @Override
    public boolean canResend(UUID userId, String purpose) {
        String dailyKey = DAILY_COUNT_PREFIX + userId + DELIMITER + purpose;
        String current = redisTemplate.opsForValue().get(dailyKey);
        if (current == null) {
            return true;
        }
        try {
            return Long.parseLong(current) < otpProperties.getDailyResendLimit();
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    @Override
    public long dailyRemaining(UUID userId, String purpose) {
        String dailyKey = DAILY_COUNT_PREFIX + userId + DELIMITER + purpose;
        String current = redisTemplate.opsForValue().get(dailyKey);
        if (current == null) {
            return otpProperties.getDailyResendLimit();
        }
        try {
            long used = Long.parseLong(current);
            return Math.max(0L, otpProperties.getDailyResendLimit() - used);
        } catch (NumberFormatException ex) {
            return otpProperties.getDailyResendLimit();
        }
    }

    private String buildHashKey(UUID userId, String purpose) {
        return KEY_PREFIX + purpose + DELIMITER + userId;
    }

    private String generateNumericCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}