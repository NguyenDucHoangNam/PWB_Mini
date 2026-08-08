package com.pwb.iam.domain.model;

public record LoginPolicy(
        int loginPerMinute,
        int refreshPerMinute,
        int googleLoginPerMinute,
        int resetPasswordPerMinute,
        int verifyOtpPerMinute,
        int changePasswordPerMinute,
        int maxFailedAttempts,
        int lockoutMinutes
) {
}
