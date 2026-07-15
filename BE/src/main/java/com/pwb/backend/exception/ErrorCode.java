package com.pwb.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INTERNAL_ERROR      ("COMMON_001", "error.internal",             HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_REQUEST         ("COMMON_002", "error.bad_request",          HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED   ("COMMON_003", "error.validation",           HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND  ("COMMON_004", "error.not_found",            HttpStatus.NOT_FOUND),
    RESOURCE_DUPLICATE  ("COMMON_005", "error.duplicate",            HttpStatus.CONFLICT),

    AUTH_UNAUTHORIZED   ("AUTH_001",   "error.unauthorized",         HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN      ("AUTH_002",   "error.forbidden",            HttpStatus.FORBIDDEN),
    AUTH_TOKEN_EXPIRED  ("AUTH_003",   "error.token_expired",        HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID  ("AUTH_004",   "error.token_invalid",        HttpStatus.UNAUTHORIZED),
    AUTH_LOGIN_FAILED   ("AUTH_005",   "error.login_failed",         HttpStatus.UNAUTHORIZED),

    USER_NOT_FOUND      ("USER_001",  "error.user.not_found",       HttpStatus.NOT_FOUND),
    USER_EMAIL_EXISTS   ("USER_002",  "error.user.email_exists",    HttpStatus.CONFLICT),
    USER_NAME_EXISTS    ("USER_003",  "error.user.name_exists",     HttpStatus.CONFLICT),

    MEDIA_UPLOAD_FAILED ("MEDIA_001", "error.media.upload_failed",  HttpStatus.INTERNAL_SERVER_ERROR),
    MEDIA_FILE_TOO_LARGE("MEDIA_002", "error.media.file_too_large", HttpStatus.BAD_REQUEST),

    RATE_LIMIT_EXCEEDED ("RATE_001",  "error.rate_limit",           HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String messageCode;
    private final HttpStatus httpStatus;
}
