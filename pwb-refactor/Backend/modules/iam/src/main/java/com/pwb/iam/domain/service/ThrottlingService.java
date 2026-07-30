package com.pwb.iam.domain.service;

import java.time.Duration;

public interface ThrottlingService {

    ThrottleDecision consume(String key, int limit, Duration window);

    long enforceCooldown(String email, CooldownPurpose purpose);

    long enforceCooldownForPasswordReset(String email);

    enum CooldownPurpose {
        REGISTER,
        RESEND_OTP,
        PASSWORD_RESET
    }

    record ThrottleDecision(boolean allowed, long retryAfterSeconds, long remaining) {
        public static ThrottleDecision allow(long remaining) {
            return new ThrottleDecision(true, 0L, remaining);
        }

        public static ThrottleDecision deny(long retryAfterSeconds) {
            return new ThrottleDecision(false, retryAfterSeconds, 0L);
        }
    }

    record CooldownResult(long remainingSeconds, boolean active) {
        public static CooldownResult inactive() {
            return new CooldownResult(0L, false);
        }

        public static CooldownResult active(long remainingSeconds) {
            return new CooldownResult(remainingSeconds, true);
        }
    }
}
