package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.OtpService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OtpServiceImpl implements OtpService {

    private static final String KEY_OTP = "otp:";
    private static final String KEY_ATTEMPT = "otp:attempt:";
    private static final String KEY_LOCK = "otp:lock:";
    private static final String KEY_LAST_SENT = "otp:last-sent:";

    private static final long DEFAULT_OTP_TTL_SECONDS = 300L;
    private static final long DEFAULT_ATTEMPT_TTL_SECONDS = 600L;
    private static final long DEFAULT_LOCKOUT_TTL_SECONDS = 900L;
    private static final long DEFAULT_RESEND_COOLDOWN_SECONDS = 60L;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final String OTP_PEPPER = "pwb-mini:otp:hash:v1";

    private static final String RESEND_SCRIPT_RESOURCE = "scripts/otp_resend.lua";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.iam.otp.ttl-seconds}")
    private long otpTtlSeconds;
    @Value("${app.iam.otp.attempt-ttl-seconds}")
    private long attemptTtlSeconds;
    @Value("${app.iam.otp.lockout-ttl-seconds}")
    private long lockoutTtlSeconds;
    @Value("${app.iam.otp.resend-cooldown-seconds}")
    private long resendCooldownSeconds;
    @Value("${app.iam.otp.max-attempts}")
    private int maxAttempts;

    private final DefaultRedisScript<List> verifyScript;
    private final DefaultRedisScript<Long> resendScript;

    public OtpServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.verifyScript = new DefaultRedisScript<>();
        this.verifyScript.setResultType(List.class);
        this.resendScript = new DefaultRedisScript<>();
        this.resendScript.setResultType(Long.class);
        try {
            String verifyBody = StreamUtils.copyToString(
                    new ClassPathResource("scripts/otp_verify.lua").getInputStream(),
                    StandardCharsets.UTF_8);
            this.verifyScript.setScriptText(verifyBody);
            String resendBody = StreamUtils.copyToString(
                    new ClassPathResource(RESEND_SCRIPT_RESOURCE).getInputStream(),
                    StandardCharsets.UTF_8);
            this.resendScript.setScriptText(resendBody);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load OTP Lua scripts", ex);
        }
    }

    @PostConstruct
    void warmUp() {
    }

    @Override
    public void issueOtp(String email, String otp) {
        String normalized = email.toLowerCase();
        String key = KEY_OTP + normalized;
        redisTemplate.opsForValue().set(key, hashOtp(normalized, otp), otpTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.delete(KEY_ATTEMPT + normalized);
        redisTemplate.delete(KEY_LOCK + normalized);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verifyOtp(String email, String otp) {
        String normalized = email.toLowerCase();
        String otpKey = KEY_OTP + normalized;
        String attemptKey = KEY_ATTEMPT + normalized;
        String lockKey = KEY_LOCK + normalized;

        List<Object> result;
        try {
            result = redisTemplate.execute(
                    verifyScript,
                    List.of(otpKey, attemptKey, lockKey),
                    hashOtp(normalized, otp),
                    Long.toString(lockoutTtlSeconds),
                    Long.toString(attemptTtlSeconds),
                    Integer.toString(maxAttempts));
        } catch (Exception ex) {
            log.error("OTP_VERIFICATION_REDIS_FAILURE emailHash={} errorType={} errorMessage={}",
                    normalized.hashCode(), ex.getClass().getSimpleName(), ex.getMessage());
            throw new BusinessException(IamErrorCode.INVALID_OTP);
        }

        if (result == null || result.size() < 2) {
            throw new BusinessException(IamErrorCode.INVALID_OTP);
        }

        long status = ((Number) result.get(0)).longValue();
        String code = String.valueOf(result.get(1));

        if (status == 1L) {
            return true;
        }
        if ("LOCKED".equals(code)) {
            throw new BusinessException(
                    IamErrorCode.OTP_LOCKED,
                    "Too many invalid OTP attempts. Retry after " + lockoutTtlSeconds + " seconds",
                    null,
                    Map.of("retryAfterSeconds", lockoutTtlSeconds));
        }
        throw new BusinessException(IamErrorCode.INVALID_OTP);
    }

    @Override
    public boolean isLocked(String email) {
        Boolean exists = redisTemplate.hasKey(KEY_LOCK + email.toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean canResend(String email) {
        Boolean exists = redisTemplate.hasKey(KEY_LAST_SENT + email.toLowerCase());
        return !Boolean.TRUE.equals(exists);
    }

    @Override
    public void markResent(String email) {
        redisTemplate.opsForValue()
                .set(KEY_LAST_SENT + email.toLowerCase(), "1", resendCooldownSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean tryAcquireResendSlot(String email) {
        String normalized = email.toLowerCase();
        Long acquired;
        try {
            acquired = redisTemplate.execute(
                    resendScript,
                    List.of(KEY_LAST_SENT + normalized),
                    Long.toString(resendCooldownSeconds));
        } catch (Exception ex) {
            log.error("OTP_RESEND_COOLDOWN_REDIS_FAILURE emailHash={} errorType={} errorMessage={}",
                    normalized.hashCode(), ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
        return acquired != null && acquired == 1L;
    }

    @Override
    public long lockoutRetryAfterSeconds() {
        return lockoutTtlSeconds;
    }

    private static String hashOtp(String email, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(OTP_PEPPER.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0x00);
            digest.update(email.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0x00);
            digest.update(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    long defaultOtpTtlSeconds() { return DEFAULT_OTP_TTL_SECONDS; }
    long defaultAttemptTtlSeconds() { return DEFAULT_ATTEMPT_TTL_SECONDS; }
    long defaultLockoutTtlSeconds() { return DEFAULT_LOCKOUT_TTL_SECONDS; }
    long defaultResendCooldownSeconds() { return DEFAULT_RESEND_COOLDOWN_SECONDS; }
    int defaultMaxAttempts() { return DEFAULT_MAX_ATTEMPTS; }
}
