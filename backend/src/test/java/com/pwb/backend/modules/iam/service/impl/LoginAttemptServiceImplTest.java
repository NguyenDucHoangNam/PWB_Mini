package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.config.LoginProperties;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private LoginProperties loginProperties;

    private LoginAttemptServiceImpl loginAttemptService;

    private final UUID userId = UUID.randomUUID();
    private final String ip = "127.0.0.1";

    @BeforeEach
    void setUp() {
        lenient().when(loginProperties.getMaxFailedAttempts()).thenReturn(5);
        lenient().when(loginProperties.getFailedAttemptWindowSeconds()).thenReturn(900L);
        lenient().when(loginProperties.getLockoutSeconds()).thenReturn(900L);
        lenient().when(loginProperties.getIpMaxFailedAttempts()).thenReturn(10);
        lenient().when(loginProperties.getIpAttemptWindowSeconds()).thenReturn(3600L);
        lenient().when(loginProperties.getIpLockoutSeconds()).thenReturn(3600L);

        loginAttemptService = new LoginAttemptServiceImpl(redisTemplate, loginProperties);
    }

    @Test
    void validateNotLocked_notLocked() {
        when(redisTemplate.hasKey("login_lockout:" + userId)).thenReturn(false);

        assertDoesNotThrow(() -> loginAttemptService.validateNotLocked(userId));
    }

    @Test
    void validateNotLocked_locked() {
        when(redisTemplate.hasKey("login_lockout:" + userId)).thenReturn(true);
        when(redisTemplate.getExpire("login_lockout:" + userId, TimeUnit.SECONDS)).thenReturn(600L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                loginAttemptService.validateNotLocked(userId)
        );
        assertEquals(IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED, exception.getErrorCode());
    }

    @Test
    void validateNotLocked_lockedWithNullTtl() {
        when(redisTemplate.hasKey("login_lockout:" + userId)).thenReturn(true);
        when(redisTemplate.getExpire("login_lockout:" + userId, TimeUnit.SECONDS)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                loginAttemptService.validateNotLocked(userId)
        );
        assertEquals(IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailure_withinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(2L, 0L));

        assertDoesNotThrow(() -> loginAttemptService.recordFailure(userId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailure_exceedLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(5L, 1L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                loginAttemptService.recordFailure(userId)
        );
        assertEquals(IamErrorCode.ACCOUNT_TEMPORARILY_LOCKED, exception.getErrorCode());
    }

    @Test
    void validateIpNotBlocked_notBlocked() {
        when(redisTemplate.hasKey("login_lockout_ip:" + ip)).thenReturn(false);

        assertDoesNotThrow(() -> loginAttemptService.validateIpNotBlocked(ip));
    }

    @Test
    void validateIpNotBlocked_blocked() {
        when(redisTemplate.hasKey("login_lockout_ip:" + ip)).thenReturn(true);
        when(redisTemplate.getExpire("login_lockout_ip:" + ip, TimeUnit.SECONDS)).thenReturn(1800L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                loginAttemptService.validateIpNotBlocked(ip)
        );
        assertEquals(IamErrorCode.RATE_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void validateIpNotBlocked_nullIp() {
        assertDoesNotThrow(() -> loginAttemptService.validateIpNotBlocked(null));
    }

    @Test
    void clearFailures_success() {
        loginAttemptService.clearFailures(userId);

        verify(redisTemplate).delete("login_attempts:" + userId);
        verify(redisTemplate).delete("login_lockout:" + userId);
    }

    @Test
    void lockoutRetryAfterSeconds_returnsConfiguredValue() {
        long retrySeconds = loginAttemptService.lockoutRetryAfterSeconds();
        assertEquals(900L, retrySeconds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordIpFailure_withinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(5L, 0L));

        assertDoesNotThrow(() -> loginAttemptService.recordIpFailure(ip));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordIpFailure_exceedLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(10L, 1L));

        assertDoesNotThrow(() -> loginAttemptService.recordIpFailure(ip));
    }

    @Test
    void recordIpFailure_nullIp() {
        assertDoesNotThrow(() -> loginAttemptService.recordIpFailure(null));
    }

    @Test
    void clearIpFailures_success() {
        loginAttemptService.clearIpFailures(ip);

        verify(redisTemplate).delete("login_attempts_ip:" + ip);
        verify(redisTemplate).delete("login_lockout_ip:" + ip);
    }

    @Test
    void clearIpFailures_nullIp() {
        assertDoesNotThrow(() -> loginAttemptService.clearIpFailures(null));
    }
}
