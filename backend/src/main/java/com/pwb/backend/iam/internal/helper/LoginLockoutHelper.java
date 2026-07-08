package com.pwb.backend.iam.internal.helper;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public final class LoginLockoutHelper {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW_DURATION = Duration.ofMinutes(15);

    private LoginLockoutHelper() {
    }

    public static boolean isLocked(StringRedisTemplate redisTemplate, String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey(userId)));
    }

    public static void ensureNotLocked(StringRedisTemplate redisTemplate, String userId) {
        if (isLocked(redisTemplate, userId)) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
                    "Account is temporarily locked, please try again later");
        }
    }

    public static void recordFailure(StringRedisTemplate redisTemplate, String userId) {
        String attemptsKey = "login_attempts:" + userId;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, WINDOW_DURATION);
        }
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockoutKey(userId), "true", WINDOW_DURATION);
            redisTemplate.delete(attemptsKey);
            throw new BusinessException(
                    ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
                    "Account is temporarily locked, please try again later");
        }
    }

    public static void clear(StringRedisTemplate redisTemplate, String userId) {
        redisTemplate.delete("login_attempts:" + userId);
        redisTemplate.delete(lockoutKey(userId));
    }

    public static String lockoutKey(String userId) {
        return "login_lockout:" + userId;
    }

    public static String attemptsKey(String userId) {
        return "login_attempts:" + userId;
    }
}
