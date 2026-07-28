package com.pwb.iam.domain.model;

public record PasswordResetPolicy(
        String tokenSecret,
        long tokenTtlMinutes,
        long cooldownSeconds,
        String frontendUrl,
        String resetPath
) {
}
