package com.pwb.backend.modules.iam.service;

import java.util.UUID;

public interface LoginAttemptService {

    void validateNotLocked(UUID userId);

    void recordFailure(UUID userId);

    void clearFailures(UUID userId);

    long lockoutRetryAfterSeconds();
}
