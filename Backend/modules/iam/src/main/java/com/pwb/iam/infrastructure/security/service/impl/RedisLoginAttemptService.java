package com.pwb.iam.infrastructure.security.service.impl;

import com.pwb.iam.infrastructure.security.config.LoginPolicyProperties;
import com.pwb.iam.infrastructure.security.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String EMAIL_FAIL_PREFIX = "login:fail:email:";
    private static final String IP_FAIL_PREFIX = "login:fail:ip:";
    private static final String EMAIL_LOCK_PREFIX = "login:lock:email:";
    private static final String IP_LOCK_PREFIX = "login:lock:ip:";
    private static final String UNKNOWN_IP = "unknown";

    private final LoginPolicyProperties loginPolicyProperties;

    @Nullable
    @Autowired
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void recordFailure(String email, String ip) {
        if (!loginPolicyProperties.isEnabled() || stringRedisTemplate == null) {
            return;
        }

        Duration lockDuration = Duration.ofMinutes(loginPolicyProperties.getLockDurationMinutes());
        String effectiveIp = resolveIp(ip);

        handleFailureCounter(EMAIL_FAIL_PREFIX + email, lockDuration);
        handleFailureCounter(IP_FAIL_PREFIX + effectiveIp, lockDuration);

        long emailCount = getCount(EMAIL_FAIL_PREFIX + email);
        if (emailCount >= loginPolicyProperties.getMaxFailAttempts()) {
            stringRedisTemplate.opsForValue().set(
                    EMAIL_LOCK_PREFIX + email, "1", lockDuration);
            log.warn("Login email locked: email={}", email);
        }

        long ipCount = getCount(IP_FAIL_PREFIX + effectiveIp);
        if (ipCount >= loginPolicyProperties.getMaxFailAttempts()) {
            stringRedisTemplate.opsForValue().set(
                    IP_LOCK_PREFIX + effectiveIp, "1", lockDuration);
            log.warn("Login IP locked: ip={}", effectiveIp);
        }
    }

    @Override
    public void recordSuccess(String email, String ip) {
        if (stringRedisTemplate == null) {
            return;
        }
        stringRedisTemplate.delete(EMAIL_FAIL_PREFIX + email);
        stringRedisTemplate.delete(EMAIL_LOCK_PREFIX + email);
        String effectiveIp = resolveIp(ip);
        stringRedisTemplate.delete(IP_FAIL_PREFIX + effectiveIp);
        stringRedisTemplate.delete(IP_LOCK_PREFIX + effectiveIp);
    }

    @Override
    public boolean isEmailLocked(String email) {
        if (stringRedisTemplate == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(EMAIL_LOCK_PREFIX + email));
    }

    @Override
    public boolean isIpLocked(String ip) {
        if (stringRedisTemplate == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(IP_LOCK_PREFIX + resolveIp(ip)));
    }

    @Override
    public long getEmailLockRemainingSeconds(String email) {
        return getRemainingSeconds(EMAIL_LOCK_PREFIX + email);
    }

    @Override
    public long getIpLockRemainingSeconds(String ip) {
        return getRemainingSeconds(IP_LOCK_PREFIX + resolveIp(ip));
    }

    private void handleFailureCounter(String key, Duration ttl) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
    }

    private long getCount(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long getRemainingSeconds(String key) {
        if (stringRedisTemplate == null) {
            return 0L;
        }
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0L;
    }

    private String resolveIp(String ip) {
        return ip == null || ip.isBlank() ? UNKNOWN_IP : ip;
    }
}
