package com.pwb.iam.infrastructure.service.impl;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.iam.api.OtpService;
import com.pwb.iam.api.dto.response.OtpPolicyResult;
import com.pwb.iam.api.dto.response.OtpVerificationOutcome;
import com.pwb.iam.core.events.OtpIssuedDomainEvent;
import com.pwb.iam.core.events.OtpVerifiedDomainEvent;
import com.pwb.iam.core.model.OtpCode.OtpStatus;
import com.pwb.iam.core.model.OtpPurpose;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.iam.infrastructure.persistence.entity.OtpCodeJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.OtpCodeJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String KEY_PREFIX = "otp:";
    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final String COOLDOWN_PREFIX = "otp:last-sent:";
    private static final String DAILY_COUNT_PREFIX = "otp:daily-count:";
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);

    private static final RedisScript<Long> DAILY_INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]);"
            + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end;"
            + "return current;",
            Long.class);

    private final OtpProperties otpProperties;
    private final StringRedisTemplate redisTemplate;
    private final UserJpaRepository userRepository;
    private final OtpCodeJpaRepository otpCodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public OtpPolicyResult requestOtp(String email, OtpPurpose purpose) {
        UserJpaEntity user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UUID userId = user.getId();

        Duration cooldown = resendCooldownRemaining(userId, purpose);
        if (!cooldown.isZero()) {
            return OtpPolicyResult.throttled(cooldown);
        }

        if (!canResend(userId, purpose)) {
            return OtpPolicyResult.throttled(Duration.ZERO);
        }

        String code = generateNumericCode(otpProperties.getCodeLength());
        String salt = generateSalt();
        String hashed = sha256(salt + code);

        String redisKey = buildHashKey(userId, purpose);
        String attemptsKey = redisKey + ATTEMPTS_SUFFIX;
        Duration ttl = Duration.ofSeconds(otpProperties.getTtlSeconds());

        redisTemplate.opsForValue().set(redisKey, salt + ":" + hashed, ttl);
        redisTemplate.opsForValue().set(attemptsKey, "0", ttl);
        redisTemplate.opsForValue().set(
                COOLDOWN_PREFIX + userId + ":" + purpose.name(),
                "1",
                Duration.ofSeconds(otpProperties.getResendCooldownSeconds()));

        String dailyKey = DAILY_COUNT_PREFIX + userId + ":" + purpose.name();
        Long currentCount = redisTemplate.execute(
                DAILY_INCREMENT_SCRIPT,
                List.of(dailyKey),
                String.valueOf(DAILY_WINDOW.toSeconds()));

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        OtpCodeJpaEntity entity = OtpCodeJpaEntity.builder()
                .userId(userId)
                .purpose(purpose)
                .status(OtpStatus.PENDING)
                .attempts(0)
                .expiresAt(expiresAt)
                .build();
        otpCodeRepository.save(entity);

        eventPublisher.publishEvent(new OtpIssuedDomainEvent(userId, email, code, purpose, now));

        long dailyRemaining = otpProperties.getDailyResendLimit()
                - (currentCount == null ? 0 : currentCount);
        return OtpPolicyResult.allowed(Math.max(0L, dailyRemaining));
    }

    @Override
    public OtpVerificationOutcome verifyOtp(String email, OtpPurpose purpose, String rawCode) {
        UserJpaEntity user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UUID userId = user.getId();
        String redisKey = buildHashKey(userId, purpose);
        String attemptsKey = redisKey + ATTEMPTS_SUFFIX;

        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null || stored.isBlank()) {
            return OtpVerificationOutcome.expiredOrMissing(userId, email, purpose);
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        int currentAttempts = attempts == null ? 1 : attempts.intValue();

        if (currentAttempts > otpProperties.getMaxAttempts()) {
            invalidate(userId, purpose);
            otpCodeRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, OtpStatus.PENDING)
                    .ifPresent(e -> otpCodeRepository.markLocked(
                            e.getId(), currentAttempts, Instant.now()));
            return OtpVerificationOutcome.locked(userId, email, purpose);
        }

        String[] parts = stored.split(":", 2);
        if (parts.length != 2) {
            invalidate(userId, purpose);
            return OtpVerificationOutcome.expiredOrMissing(userId, email, purpose);
        }
        String salt = parts[0];
        String hash = parts[1];
        boolean matched = MessageDigest.isEqual(
                sha256(salt + rawCode).getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8));

        if (!matched) {
            if (currentAttempts >= otpProperties.getMaxAttempts()) {
                invalidate(userId, purpose);
                otpCodeRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, OtpStatus.PENDING)
                        .ifPresent(e -> otpCodeRepository.markLocked(
                                e.getId(), currentAttempts, Instant.now()));
                return OtpVerificationOutcome.locked(userId, email, purpose);
            }
            return OtpVerificationOutcome.invalid(userId, email, purpose);
        }

        invalidate(userId, purpose);
        otpCodeRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, OtpStatus.PENDING)
                .ifPresent(e -> otpCodeRepository.markVerified(
                        e.getId(), OtpStatus.VERIFIED, Instant.now()));

        Instant now = Instant.now();
        eventPublisher.publishEvent(new OtpVerifiedDomainEvent(userId, email, purpose, now));

        return OtpVerificationOutcome.ok(userId, email, purpose, now);
    }

    @Override
    public OtpVerificationOutcome verifyOtpByUserId(UUID userId, OtpPurpose purpose, String rawCode) {
        UserJpaEntity user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return verifyOtp(user.getEmail(), purpose, rawCode);
    }

    private void invalidate(UUID userId, OtpPurpose purpose) {
        String redisKey = buildHashKey(userId, purpose);
        redisTemplate.delete(redisKey);
        redisTemplate.delete(redisKey + ATTEMPTS_SUFFIX);
    }

    private Duration resendCooldownRemaining(UUID userId, OtpPurpose purpose) {
        String key = COOLDOWN_PREFIX + userId + ":" + purpose.name();
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl <= 0) return Duration.ZERO;
        return Duration.ofSeconds(ttl);
    }

    private boolean canResend(UUID userId, OtpPurpose purpose) {
        String dailyKey = DAILY_COUNT_PREFIX + userId + ":" + purpose.name();
        String current = redisTemplate.opsForValue().get(dailyKey);
        if (current == null) return true;
        try {
            return Long.parseLong(current) < otpProperties.getDailyResendLimit();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String buildHashKey(UUID userId, OtpPurpose purpose) {
        return KEY_PREFIX + userId + ":" + purpose.name();
    }

    private String generateNumericCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
