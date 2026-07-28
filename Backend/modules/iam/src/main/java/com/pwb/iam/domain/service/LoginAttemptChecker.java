package com.pwb.iam.domain.service;

public interface LoginAttemptChecker {

    void recordFailure(String email, String ip);

    void recordSuccess(String email, String ip);

    boolean isEmailLocked(String email);

    boolean isIpLocked(String ip);

    void handleFailureAndThrow(String email, String ip);
}
