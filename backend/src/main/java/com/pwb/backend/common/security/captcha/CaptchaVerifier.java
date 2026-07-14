package com.pwb.backend.common.security.captcha;

import com.pwb.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaVerifier {

    private final TurnstileProperties properties;
    private final TurnstileHttpClient httpClient;
    private final CaptchaChallengeTracker challengeTracker;

    public void verifyOrThrow(String captchaToken, HttpServletRequest request, CaptchaContext context) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!challengeTracker.requiresCaptcha(context)) {
            log.debug("CAPTCHA_BYPASSED scope={} identifier={}", context.scope(), context.identifier());
            return;
        }
        log.info("CAPTCHA_CHALLENGE_TRIGGERED scope={} identifier={}", context.scope(), context.identifier());
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BusinessException(CaptchaErrorCode.CAPTCHA_MISSING);
        }
        String remoteIp = request == null ? null : request.getRemoteAddr();
        TurnstileVerificationResponse response;
        try {
            response = httpClient.verify(captchaToken, remoteIp, properties);
        } catch (Exception ex) {
            if (properties.isFailOpen()) {
                log.warn("CAPTCHA_FAIL_OPEN_ENGAGED scope={} error={}", context.scope(), ex.getMessage());
                return;
            }
            log.error("CAPTCHA_VERIFICATION_FAILED scope={} error={}", context.scope(), ex.getMessage());
            throw new BusinessException(CaptchaErrorCode.CAPTCHA_SERVICE_UNAVAILABLE);
        }
        if (response == null || !response.success()) {
            log.warn("CAPTCHA_REJECTED scope={} errors={}",
                    context.scope(), response == null ? "null-response" : response.errorCodes());
            throw new BusinessException(CaptchaErrorCode.CAPTCHA_INVALID);
        }
    }

    public long recordFailure(CaptchaContext context) {
        return challengeTracker.recordFailure(context);
    }

    public void clearFailure(CaptchaContext context) {
        challengeTracker.clearFailure(context);
    }
}
