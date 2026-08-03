package com.pwb.iam.domain.model;

public record LoginPolicy(
        int loginPerMinute,
        int refreshPerMinute,
        int googleLoginPerMinute,
        int maxFailedAttempts,
        int lockoutMinutes
) {
}
