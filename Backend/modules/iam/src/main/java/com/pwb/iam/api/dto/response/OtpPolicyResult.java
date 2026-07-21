package com.pwb.iam.api.dto.response;

import java.time.Duration;

public record OtpPolicyResult(
        boolean allowed,
        Duration cooldownRemaining,
        long dailyRemaining,
        ThrottleType throttleType
) {

    public static OtpPolicyResult allowed(long dailyRemaining) {
        return new OtpPolicyResult(true, Duration.ZERO, dailyRemaining, ThrottleType.NONE);
    }

    public static OtpPolicyResult cooldownThrottled(Duration cooldownRemaining) {
        return new OtpPolicyResult(false, cooldownRemaining, 0L, ThrottleType.COOLDOWN);
    }

    public static OtpPolicyResult dailyLimitThrottled(long dailyLimit) {
        return new OtpPolicyResult(false, Duration.ZERO, 0L, ThrottleType.DAILY_LIMIT);
    }
}