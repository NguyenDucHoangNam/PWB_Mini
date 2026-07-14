package com.pwb.backend.common.security.captcha;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum CaptchaErrorCode implements ErrorCode {

    CAPTCHA_MISSING("CAPTCHA_MISSING", "Captcha token is required", HttpStatus.BAD_REQUEST),
    CAPTCHA_INVALID("CAPTCHA_INVALID", "Captcha verification failed", HttpStatus.BAD_REQUEST),
    CAPTCHA_SERVICE_UNAVAILABLE("CAPTCHA_SERVICE_UNAVAILABLE", "Captcha service is unavailable", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
