package com.pwb.backend.service;

import java.time.Duration;
import java.util.UUID;

public interface OtpService {

    String PURPOSE_REGISTER = "register";

    String generateAndStore(UUID userId, String purpose);

    VerificationResult verify(UUID userId, String purpose, String code);

    void invalidate(UUID userId, String purpose);

    Duration resendCooldownRemaining(UUID userId, String purpose);

    boolean canResend(UUID userId, String purpose);

    long dailyRemaining(UUID userId, String purpose);

    enum VerificationResult {
        OK,
        INVALID,
        EXPIRED_OR_MISSING,
        LOCKED
    }
}