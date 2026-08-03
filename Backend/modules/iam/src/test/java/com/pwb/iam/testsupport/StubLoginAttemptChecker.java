package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.LoginAttemptChecker;

public final class StubLoginAttemptChecker implements LoginAttemptChecker {

    private boolean locked = false;
    private long retryAfterSeconds = 0L;
    private int failureCount = 0;
    private int resetCount = 0;
    private int resetIpLockCount = 0;

    public StubLoginAttemptChecker notLocked() {
        this.locked = false;
        this.retryAfterSeconds = 0L;
        return this;
    }

    public StubLoginAttemptChecker locked(long retryAfterSeconds) {
        this.locked = true;
        this.retryAfterSeconds = retryAfterSeconds;
        return this;
    }

    @Override
    public void recordFailure(String email, String clientIp) {
        failureCount++;
    }

    @Override
    public void reset(String email) {
        resetCount++;
    }

    @Override
    public void resetIpLock(String email, String clientIp) {
        resetIpLockCount++;
    }

    @Override
    public LockState isLocked(String email, String clientIp) {
        if (locked) {
            return LockState.locked(retryAfterSeconds);
        }
        return LockState.notLocked();
    }

    public int failureCount() {
        return failureCount;
    }

    public int resetCount() {
        return resetCount;
    }

    public int resetIpLockCount() {
        return resetIpLockCount;
    }
}
