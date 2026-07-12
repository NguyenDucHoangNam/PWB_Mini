package com.pwb.backend.modules.share.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum ShareErrorCode implements ErrorCode {

    INVALID_RECIPIENT_EMAIL("INVALID_RECIPIENT_EMAIL",
            "Recipient email is invalid or belongs to a blacklisted domain",
            HttpStatus.BAD_REQUEST),

    FORBIDDEN_ACCESS("FORBIDDEN_ACCESS",
            "You do not own this demo",
            HttpStatus.FORBIDDEN),

    DEMO_NOT_FOUND("DEMO_NOT_FOUND",
            "Demo not found",
            HttpStatus.NOT_FOUND),

    DEMO_NOT_ACTIVE("DEMO_NOT_ACTIVE",
            "Demo is not in ACTIVE state and cannot be shared",
            HttpStatus.CONFLICT),

    SHARE_QUOTA_EXCEEDED("SHARE_QUOTA_EXCEEDED",
            "Daily share quota exceeded (100 unique recipients / 500 distributions per 24h)",
            HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}