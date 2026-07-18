package com.pwb.iam.infrastructure.security.service;

public interface LoginAttemptService {

    void recordFailure(String email, String ip);

    void recordSuccess(String email, String ip);

    boolean isEmailLocked(String email);

    boolean isIpLocked(String ip);

    long getEmailLockRemainingSeconds(String email);

    long getIpLockRemainingSeconds(String ip);
}
