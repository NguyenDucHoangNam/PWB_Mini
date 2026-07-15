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

    AUTH_UNAUTHORIZED       ("AUTH_001", "error.unauthorized",              HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN          ("AUTH_002", "error.forbidden",                 HttpStatus.FORBIDDEN),
    AUTH_TOKEN_EXPIRED      ("AUTH_003", "error.token_expired",             HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID      ("AUTH_004", "error.token_invalid",             HttpStatus.UNAUTHORIZED),
    AUTH_LOGIN_FAILED       ("AUTH_005", "error.login_failed",              HttpStatus.UNAUTHORIZED),
    AUTH_OTP_INVALID        ("AUTH_006", "error.otp.invalid_code",          HttpStatus.BAD_REQUEST),
    AUTH_OTP_LOCKED         ("AUTH_007", "error.otp.attempts_exceeded",     HttpStatus.TOO_MANY_REQUESTS),
    AUTH_OTP_RATE_LIMIT     ("AUTH_008", "error.otp.rate_limit",            HttpStatus.TOO_MANY_REQUESTS),
    AUTH_OTP_DAILY_LIMIT    ("AUTH_009", "error.otp.daily_limit",           HttpStatus.TOO_MANY_REQUESTS),
    AUTH_ACCOUNT_NOT_VERIFIED("AUTH_010", "error.account.not_verified",     HttpStatus.FORBIDDEN),

    AUTH_GOOGLE_TOKEN_INVALID      ("AUTH_011", "error.google.token_invalid",            HttpStatus.UNAUTHORIZED),
    AUTH_GOOGLE_EMAIL_NOT_VERIFIED ("AUTH_012", "error.google.email_not_verified",       HttpStatus.UNAUTHORIZED),
    AUTH_OAUTH_USER_NO_PASSWORD    ("AUTH_013", "error.oauth.no_password",               HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_INVALID       ("AUTH_014", "error.password_reset.token_invalid",    HttpStatus.BAD_REQUEST),
    AUTH_INVALID_CURRENT_PASSWORD  ("AUTH_015", "error.password.current_invalid",        HttpStatus.BAD_REQUEST),
    AUTH_PASSWORD_REUSED           ("AUTH_016", "error.password.reused",                  HttpStatus.BAD_REQUEST),
    AUTH_PASSWORD_RESET_COOLDOWN   ("AUTH_021", "error.password_reset.cooldown",          HttpStatus.TOO_MANY_REQUESTS),

    USER_NOT_FOUND         ("USER_001",  "error.user.not_found",       HttpStatus.NOT_FOUND),
    USER_EMAIL_EXISTS      ("USER_002",  "error.user.email_exists",    HttpStatus.CONFLICT),
    USER_NAME_EXISTS       ("USER_003",  "error.user.name_exists",     HttpStatus.CONFLICT),
    USER_NAME_INVALID      ("USER_004",  "error.user.name_invalid",    HttpStatus.BAD_REQUEST),

    MEDIA_UPLOAD_FAILED ("MEDIA_001", "error.media.upload_failed",  HttpStatus.INTERNAL_SERVER_ERROR),
    MEDIA_FILE_TOO_LARGE("MEDIA_002", "error.media.file_too_large", HttpStatus.BAD_REQUEST),

    RATE_LIMIT_EXCEEDED ("RATE_001",  "error.rate_limit",           HttpStatus.TOO_MANY_REQUESTS),

    SEEDER_ROLE_NOT_FOUND("SEEDER_001", "error.seeder.role_not_found", HttpStatus.INTERNAL_SERVER_ERROR),
    SEEDER_ROLE_INVALID  ("SEEDER_002", "error.seeder.role_not_found", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String messageCode;
    private final HttpStatus httpStatus;
}