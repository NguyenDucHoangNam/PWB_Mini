package com.pwb.backend.modules.iam.exception;

import com.pwb.backend.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum IamErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email already registered", HttpStatus.CONFLICT),
    EMAIL_DISPOSABLE("EMAIL_DISPOSABLE", "Disposable email address not allowed", HttpStatus.BAD_REQUEST),
    INVALID_OTP("INVALID_OTP", "Invalid or expired OTP code", HttpStatus.BAD_REQUEST),
    OTP_LOCKED("OTP_LOCKED", "Too many invalid OTP attempts, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_COOLDOWN("OTP_RESEND_COOLDOWN", "Please wait before requesting a new OTP", HttpStatus.TOO_MANY_REQUESTS),
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_VERIFIED("USER_ALREADY_VERIFIED", "User already verified", HttpStatus.CONFLICT),
    WEAK_PASSWORD("WEAK_PASSWORD", "Password does not meet security requirements", HttpStatus.BAD_REQUEST),

    BAD_CREDENTIALS("BAD_CREDENTIALS", "Invalid username or password", HttpStatus.BAD_REQUEST),
    ACCOUNT_BANNED("ACCOUNT_BANNED", "Account has been disabled", HttpStatus.BAD_REQUEST),
    REGISTRATION_IN_PROGRESS("REGISTRATION_IN_PROGRESS", "Account pending OTP verification", HttpStatus.BAD_REQUEST),
    ACCOUNT_TEMPORARILY_LOCKED("ACCOUNT_TEMPORARILY_LOCKED", "Account temporarily locked due to too many failed attempts", HttpStatus.LOCKED),
    INVALID_OAUTH_TOKEN("INVALID_OAUTH_TOKEN", "Google authentication token is invalid or expired", HttpStatus.BAD_REQUEST),

    JWT_EXPIRED("JWT_EXPIRED", "Access token has expired", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "Refresh token is missing or expired", HttpStatus.UNAUTHORIZED),
    TOKEN_THEFT_DETECTED("TOKEN_THEFT_DETECTED", "Token reuse detected, all sessions have been revoked", HttpStatus.UNAUTHORIZED),

    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Too many requests", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
