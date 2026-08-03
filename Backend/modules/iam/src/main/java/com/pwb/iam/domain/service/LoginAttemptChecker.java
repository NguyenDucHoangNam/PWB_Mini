package com.pwb.iam.domain.service;

public interface LoginAttemptChecker {

    void recordFailure(String email, String clientIp);

    void reset(String email);

    void resetIpLock(String email, String clientIp);

    LockState isLocked(String email, String clientIp);

    record LockState(boolean locked, long retryAfterSeconds) {
        public static LockState notLocked() {
            return new LockState(false, 0L);
        }

        public static LockState locked(long retryAfterSeconds) {
            return new LockState(true, retryAfterSeconds);
        }
    }
}