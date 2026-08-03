package com.pwb.iam.domain.model;

import java.time.Duration;

public record OtpPolicy(
        int ttlMinutes,
        int resendCooldownSeconds,
        int maxAttempts,
        int codeLength,
        int dailyLimit
) {

    public OtpPolicy {
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("ttlMinutes must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength must be positive");
        }
    }

    public Duration ttl() {
        return Duration.ofMinutes(ttlMinutes);
    }

    public Duration resendCooldown() {
        return Duration.ofSeconds(resendCooldownSeconds);
    }
}
