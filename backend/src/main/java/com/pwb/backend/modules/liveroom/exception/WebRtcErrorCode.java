package com.pwb.backend.modules.liveroom.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum WebRtcErrorCode implements ErrorCode {

    TURN_SECRET_NOT_CONFIGURED("TURN_SECRET_NOT_CONFIGURED",
            "TURN static secret is not configured on the server",
            HttpStatus.INTERNAL_SERVER_ERROR),

    INVALID_SIGNALLING_FRAME("INVALID_SIGNALLING_FRAME",
            "Signalling frame is malformed or missing required fields",
            HttpStatus.BAD_REQUEST),

    SIGNALLING_RATE_LIMITED("SIGNALLING_RATE_LIMITED",
            "Too many signalling frames; please slow down",
            HttpStatus.TOO_MANY_REQUESTS),

    TURN_CREDENTIALS_FAILED("TURN_CREDENTIALS_FAILED",
            "Failed to generate TURN credentials",
            HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
