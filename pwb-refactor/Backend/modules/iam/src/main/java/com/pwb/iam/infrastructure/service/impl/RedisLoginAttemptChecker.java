package com.pwb.iam.infrastructure.service.impl;

import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.infrastructure.config.LoginPolicyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLoginAttemptChecker implements LoginAttemptChecker {

    private static final String EMAIL_FAIL_PREFIX = "iam:login:fail:email:";
    private static final String IP_FAIL_PREFIX = "iam:login:fail:ip:";
    private static final String EMAIL_LOCK_PREFIX = "iam:login:lock:email:";
    private static final String IP_LOCK_PREFIX = "iam:login:lock:ip:";

    private final StringRedisTemplate redis;
    private final LoginPolicyProperties policy;

    @Override
    public void recordFailure(String email, String clientIp) {
        Duration failWindow = Duration.ofMinutes(policy.getLockMinutes());
        if (email != null) {
            String failKey = EMAIL_FAIL_PREFIX + email.toLowerCase();
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, failWindow);
            }
            if (count != null && count >= policy.getMaxFailures()) {
                redis.opsForValue().set(EMAIL_LOCK_PREFIX + email.toLowerCase(), "1", failWindow);
                log.warn("Account locked due to too many failures: email={} failures={}", email, count);
            }
        }
        if (clientIp != null && !clientIp.isBlank()) {
            String failKey = IP_FAIL_PREFIX + clientIp;
            Duration ipWindow = Duration.ofMinutes(policy.getIpLockMinutes());
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, ipWindow);
            }
            if (count != null && count >= policy.getIpMaxFailures()) {
                redis.opsForValue().set(IP_LOCK_PREFIX + clientIp, "1", ipWindow);
                log.warn("IP locked due to too many failures: ip={} failures={}", clientIp, count);
            }
        }
    }

    @Override
    public void reset(String email) {
        if (email == null) {
            return;
        }
        String normalized = email.toLowerCase();
        redis.delete(EMAIL_FAIL_PREFIX + normalized);
        redis.delete(EMAIL_LOCK_PREFIX + normalized);
    }

    @Override
    public void resetIpLock(String email, String clientIp) {
        if (email != null) {
            redis.delete(IP_FAIL_PREFIX + email.toLowerCase());
        }
        if (clientIp != null && !clientIp.isBlank()) {
            redis.delete(IP_FAIL_PREFIX + clientIp);
            redis.delete(IP_LOCK_PREFIX + clientIp);
        }
    }

    @Override
    public LockState isLocked(String email, String clientIp) {
        if (email != null) {
            Long ttl = redis.getExpire(EMAIL_LOCK_PREFIX + email.toLowerCase());
            if (ttl != null && ttl > 0) {
                return LockState.locked(ttl);
            }
        }
        if (clientIp != null && !clientIp.isBlank()) {
            Long ttl = redis.getExpire(IP_LOCK_PREFIX + clientIp);
            if (ttl != null && ttl > 0) {
                return LockState.locked(ttl);
            }
        }
        return LockState.notLocked();
    }
}