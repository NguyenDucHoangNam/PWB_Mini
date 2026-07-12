package com.pwb.backend.common.security.captcha;

import java.util.List;

public record TurnstileVerificationResponse(
        boolean success,
        List<String> errorCodes) {
}
