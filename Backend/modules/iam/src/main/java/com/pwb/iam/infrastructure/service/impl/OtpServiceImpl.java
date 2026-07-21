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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final RedisScript<Long> OTP_REQUEST_SCRIPT = new DefaultRedisScript<>(buildOtpRequestScript(), Long.class);

    private static final RedisScript<String> OTP_VERIFY_SCRIPT = new DefaultRedisScript<>(buildOtpVerifyScript(), String.class);

    private static String buildOtpRequestScript() {
        return """
            local otpKey = KEYS[1]
            local attemptsKey = KEYS[2]
            local cooldownKey = KEYS[3]
            local dailyKey = KEYS[4]
            local otpValue = ARGV[1]
            local ttlSeconds = tonumber(ARGV[2])
            local cooldownSeconds = tonumber(ARGV[3])
            local dailyWindowSeconds = tonumber(ARGV[4])
            local dailyLimit = tonumber(ARGV[5])

            -- Check cooldown first (atomic) - return -1 if active
            local cooldownTtl = redis.call('TTL', cooldownKey)
            if cooldownTtl > 0 then
                return -1
            end

            -- Check daily limit - return -2 if exceeded
            local dailyCount = redis.call('GET', dailyKey)
            if dailyCount then
                local count = tonumber(dailyCount)
                if count >= dailyLimit then
                    return -2
                end
            end

            -- Atomic set OTP key with TTL
            redis.call('SET', otpKey, otpValue, 'EX', ttlSeconds)

            -- Atomic set attempts key with TTL (initialize to 0)
            redis.call('SET', attemptsKey, '0', 'EX', ttlSeconds)

            -- Atomic set cooldown key with TTL
            redis.call('SET', cooldownKey, '1', 'EX', cooldownSeconds)

            -- Increment and set TTL for daily counter
            local newCount = redis.call('INCR', dailyKey)
            if newCount == 1 then
                redis.call('EXPIRE', dailyKey, dailyWindowSeconds)
            end

            -- Return current daily count (>= 0 means success)
            return newCount
            """;
    }

    private static String buildOtpVerifyScript() {
        return """
            local otpKey = KEYS[1]
            local attemptsKey = KEYS[2]
            local maxAttempts = tonumber(ARGV[1])
            local ttlSeconds = tonumber(ARGV[2])
            local providedHash = ARGV[3]

            -- Get stored OTP value
            local stored = redis.call('GET', otpKey)
            if not stored or stored == '' then
                return 'EXPIRED'
            end

            -- Increment attempts atomically
            local attempts = redis.call('INCR', attemptsKey)
            if attempts == 1 then
                redis.call('EXPIRE', attemptsKey, ttlSeconds)
            end

            -- Check if max attempts exceeded
            if attempts > maxAttempts then
                -- Delete OTP and attempts keys
                redis.call('DEL', otpKey, attemptsKey)
                return 'LOCKED'
            end

            -- Parse stored value (format: salt:hash)
            local colonPos = string.find(stored, ':')
            if not colonPos then
                redis.call('DEL', otpKey, attemptsKey)
                return 'EXPIRED'
            end

            local storedSalt = string.sub(stored, 1, colonPos - 1)
            local storedHash = string.sub(stored, colonPos + 1)

            -- Constant-time comparison
            if storedHash ~= providedHash then
                return 'INVALID'
            end

            -- Success - delete OTP and return attempts count
            redis.call('DEL', otpKey, attemptsKey)
            return 'OK'
            """;
    }

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
        String otpKey = buildHashKey(userId, purpose);
        String attemptsKey = otpKey + ATTEMPTS_SUFFIX;
        String cooldownKey = COOLDOWN_PREFIX + userId + ":" + purpose.name();
        String dailyKey = DAILY_COUNT_PREFIX + userId + ":" + purpose.name();

        otpCodeRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, OtpStatus.LOCKED)
                .ifPresent(lockedEntity -> {
                    int updated = otpCodeRepository.markExpiredFromLocked(
                            lockedEntity.getId(), OtpStatus.EXPIRED, Instant.now());
                    if (updated > 0) {
                        log.info("Reset LOCKED OTP entity on resend: userId={} otpId={}",
                                userId, lockedEntity.getId());
                    }
                });

        String code = generateNumericCode(otpProperties.getCodeLength());
        String salt = generateSalt();
        String hashed = sha256(salt + code);
        String otpValue = salt + ":" + hashed;

        String ttlSeconds = String.valueOf(otpProperties.getTtlSeconds());
        String cooldownSeconds = String.valueOf(otpProperties.getResendCooldownSeconds());
        String dailyWindowSeconds = String.valueOf(DAILY_WINDOW.toSeconds());
        String dailyLimit = String.valueOf(otpProperties.getDailyResendLimit());

        Long result = redisTemplate.execute(
                OTP_REQUEST_SCRIPT,
                List.of(otpKey, attemptsKey, cooldownKey, dailyKey),
                otpValue,
                ttlSeconds,
                cooldownSeconds,
                dailyWindowSeconds,
                dailyLimit
        );

        if (result == null) {
            log.error("OTP request script returned null: userId={}", userId);
            return OtpPolicyResult.cooldownThrottled(Duration.ZERO);
        }

        if (result < 0) {
            if (result == -1) {
                Long ttl = redisTemplate.getExpire(cooldownKey, java.util.concurrent.TimeUnit.SECONDS);
                Duration cooldown = ttl != null && ttl > 0
                        ? Duration.ofSeconds(ttl)
                        : Duration.ofSeconds(otpProperties.getResendCooldownSeconds());
                log.info("OTP request throttled by cooldown: userId={} remaining={}s",
                        userId, cooldown.toSeconds());
                return OtpPolicyResult.cooldownThrottled(cooldown);
            }
            if (result == -2) {
                log.info("OTP request throttled by daily limit: userId={}", userId);
                return OtpPolicyResult.dailyLimitThrottled(otpProperties.getDailyResendLimit());
            }
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofSeconds(otpProperties.getTtlSeconds()));
        OtpCodeJpaEntity entity = OtpCodeJpaEntity.builder()
                .userId(userId)
                .purpose(purpose)
                .status(OtpStatus.PENDING)
                .attempts(0)
                .expiresAt(expiresAt)
                .build();
        otpCodeRepository.save(entity);

        eventPublisher.publishEvent(new OtpIssuedDomainEvent(userId, email, code, purpose, now));

        long dailyRemaining = Math.max(0L, otpProperties.getDailyResendLimit() - result);
        return OtpPolicyResult.allowed(dailyRemaining);
    }

    @Override
    public OtpVerificationOutcome verifyOtp(String email, OtpPurpose purpose, String rawCode) {
        UserJpaEntity user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UUID userId = user.getId();
        String redisKey = buildHashKey(userId, purpose);
        String attemptsKey = redisKey + ATTEMPTS_SUFFIX;

        String storedValue = redisTemplate.opsForValue().get(redisKey);
        if (storedValue == null || storedValue.isBlank()) {
            return OtpVerificationOutcome.expiredOrMissing(userId, email, purpose);
        }

        int colonPos = storedValue.indexOf(':');
        if (colonPos < 0) {
            redisTemplate.delete(redisKey);
            return OtpVerificationOutcome.expiredOrMissing(userId, email, purpose);
        }

        String storedSalt = storedValue.substring(0, colonPos);
        String storedHash = storedValue.substring(colonPos + 1);

        String codeHash = sha256(storedSalt + rawCode);
        boolean matches = MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                codeHash.getBytes(StandardCharsets.UTF_8)
        );

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, Duration.ofSeconds(otpProperties.getTtlSeconds()));
        }

        if (attempts != null && attempts > otpProperties.getMaxAttempts()) {
            redisTemplate.delete(List.of(redisKey, attemptsKey));
            otpCodeRepository.findLatestByUserIdAndPurposeAndStatus(userId, purpose, OtpStatus.PENDING)
                    .ifPresent(e -> otpCodeRepository.markLocked(
                            e.getId(), otpProperties.getMaxAttempts() + 1, Instant.now()));
            return OtpVerificationOutcome.locked(userId, email, purpose);
        }

        if (!matches) {
            return OtpVerificationOutcome.invalid(userId, email, purpose);
        }

        redisTemplate.delete(List.of(redisKey, attemptsKey));
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

    private String buildHashKey(UUID userId, OtpPurpose purpose) {
        return KEY_PREFIX + userId + ":" + purpose.name();
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
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
