package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpServiceImpl otpService;

    private final String email = "test@example.com";
    private final String otp = "123456";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        otpService = new OtpServiceImpl(redisTemplate);

        // Inject configuration values using ReflectionTestUtils since they are @Value fields
        ReflectionTestUtils.setField(otpService, "otpTtlSeconds", 300L);
        ReflectionTestUtils.setField(otpService, "attemptTtlSeconds", 600L);
        ReflectionTestUtils.setField(otpService, "lockoutTtlSeconds", 900L);
        ReflectionTestUtils.setField(otpService, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 5);
    }

    @Test
    void issueOtp_shouldSaveToRedisAndDeleteAttemptsAndLocks() {
        otpService.issueOtp(email, otp);

        verify(valueOperations).set(
                eq("otp:test@example.com"),
                anyString(),
                eq(300L),
                eq(TimeUnit.SECONDS)
        );
        verify(redisTemplate).delete("otp:attempt:test@example.com");
        verify(redisTemplate).delete("otp:lock:test@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_success() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("otp:test@example.com", "otp:attempt:test@example.com", "otp:lock:test@example.com")),
                anyString(), eq("900"), eq("600"), eq("5")
        )).thenReturn(List.of(1L, "SUCCESS"));

        assertTrue(otpService.verifyOtp(email, otp));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_locked() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("otp:test@example.com", "otp:attempt:test@example.com", "otp:lock:test@example.com")),
                anyString(), eq("900"), eq("600"), eq("5")
        )).thenReturn(List.of(0L, "LOCKED"));

        BusinessException exception = assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, otp));
        assertEquals(IamErrorCode.OTP_LOCKED, exception.getErrorCode());
        assertEquals("900", String.valueOf(exception.getDetails().get("retryAfterSeconds")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_invalidOtp() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("otp:test@example.com", "otp:attempt:test@example.com", "otp:lock:test@example.com")),
                anyString(), eq("900"), eq("600"), eq("5")
        )).thenReturn(List.of(0L, "INVALID"));

        BusinessException exception = assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, otp));
        assertEquals(IamErrorCode.INVALID_OTP, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyOtp_redisThrowsException_shouldThrowInvalidOtpException() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(new RuntimeException("Redis connection lost"));

        BusinessException exception = assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, otp));
        assertEquals(IamErrorCode.INVALID_OTP, exception.getErrorCode());
    }

    @Test
    void isLocked_true() {
        when(redisTemplate.hasKey("otp:lock:test@example.com")).thenReturn(true);
        assertTrue(otpService.isLocked(email));
    }

    @Test
    void isLocked_false() {
        when(redisTemplate.hasKey("otp:lock:test@example.com")).thenReturn(false);
        assertFalse(otpService.isLocked(email));
    }

    @Test
    void canResend_true() {
        when(redisTemplate.hasKey("otp:last-sent:test@example.com")).thenReturn(false);
        assertTrue(otpService.canResend(email));
    }

    @Test
    void canResend_false() {
        when(redisTemplate.hasKey("otp:last-sent:test@example.com")).thenReturn(true);
        assertFalse(otpService.canResend(email));
    }

    @Test
    void markResent_shouldSetKeyInRedis() {
        otpService.markResent(email);
        verify(valueOperations).set(
                eq("otp:last-sent:test@example.com"),
                eq("1"),
                eq(60L),
                eq(TimeUnit.SECONDS)
        );
    }
}
