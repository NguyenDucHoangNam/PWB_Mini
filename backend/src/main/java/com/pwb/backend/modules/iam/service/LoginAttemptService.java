package com.pwb.backend.modules.iam.service;

import java.util.UUID;

public interface LoginAttemptService {

    void validateNotLocked(UUID userId);

    void recordFailure(UUID userId);

    void clearFailures(UUID userId);

    long lockoutRetryAfterSeconds();

    void validateIpNotBlocked(String clientIp);

    void recordIpFailure(String clientIp);

    void clearIpFailures(String clientIp);
}
