package com.pwb.iam.api.dto.response;

import java.time.Duration;

public record OtpPolicyResult(
        boolean allowed,
        String code,
        Duration cooldownRemaining,
        long dailyRemaining
) {

    public static OtpPolicyResult allowed(long dailyRemaining) {
        return new OtpPolicyResult(true, null, Duration.ZERO, dailyRemaining);
    }

    public static OtpPolicyResult throttled(String code, Duration cooldownRemaining) {
        return new OtpPolicyResult(false, code, cooldownRemaining, 0L);
    }
}
