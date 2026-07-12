package com.pwb.backend.common.security.captcha;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CaptchaVerifier {

    private final TurnstileProperties properties;
    private final TurnstileHttpClient httpClient;

    public void verifyOrThrow(String captchaToken, HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return;
        }
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BusinessException(IamErrorCode.CAPTCHA_INVALID);
        }
        String remoteIp = request == null ? null : request.getRemoteAddr();
        TurnstileVerificationResponse response;
        try {
            response = httpClient.verify(captchaToken, remoteIp, properties);
        } catch (Exception ex) {
            if (properties.isFailOpen()) {
                log.warn("CAPTCHA_FAIL_OPEN error={}", ex.getMessage());
                return;
            }
            log.error("CAPTCHA_VERIFICATION_FAILED error={}", ex.getMessage());
            throw new BusinessException(IamErrorCode.CAPTCHA_INVALID);
        }
        if (response == null || !response.success()) {
            log.warn("CAPTCHA_REJECTED errors={}", response == null ? "null-response" : response.errorCodes());
            throw new BusinessException(IamErrorCode.CAPTCHA_INVALID);
        }
    }
}
