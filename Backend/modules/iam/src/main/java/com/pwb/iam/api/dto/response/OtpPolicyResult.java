package com.pwb.iam.api.dto.response;

import java.time.Duration;

public record OtpPolicyResult(
        boolean allowed,
        Duration cooldownRemaining,
        long dailyRemaining
) {

    public static OtpPolicyResult allowed(long dailyRemaining) {
        return new OtpPolicyResult(true, Duration.ZERO, dailyRemaining);
    }

    public static OtpPolicyResult throttled(Duration cooldownRemaining) {
        return new OtpPolicyResult(false, cooldownRemaining, 0L);
    }
}
