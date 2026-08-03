package com.pwb.iam.domain.service;

import java.time.Duration;

public interface ThrottlingService {

    ThrottleDecision consume(String key, int limit, Duration window);

    /**
     * Starts the cooldown for the address if none is running.
     *
     * @return seconds still to wait, or {@code 0} when the caller may proceed
     */
    long enforceCooldown(String email, CooldownPurpose purpose);

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

}
