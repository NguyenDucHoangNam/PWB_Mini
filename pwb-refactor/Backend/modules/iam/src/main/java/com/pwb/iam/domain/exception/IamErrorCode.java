package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IamErrorCode implements ErrorCode {

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "IAM_001", "Email already registered."),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "IAM_002", "Password is too weak."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "IAM_003", "User not found."),
    LOGIN_BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "IAM_004", "Invalid email or password."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "IAM_005", "Account is temporarily locked due to too many failed login attempts."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "IAM_006", "Account is inactive."),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "IAM_007", "Account is not verified yet."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "IAM_008", "Authentication token is invalid."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "IAM_009", "Authentication token has expired."),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "IAM_010", "Authentication token is required."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "IAM_011", "Refresh token is invalid."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "IAM_012", "Refresh token has expired."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "IAM_013", "Refresh token has been revoked."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "IAM_014", "Too many requests. Please slow down."),
    ROLE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "IAM_018", "Default role does not exist in database."),
    AUTH_OTP_INVALID(HttpStatus.BAD_REQUEST, "IAM_021", "Invalid OTP code."),
    AUTH_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "IAM_022", "OTP code has expired or does not exist."),
    AUTH_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "IAM_026", "Too many requests. Please try again later."),
    AUTH_OTP_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "IAM_027", "You have reached the daily OTP request limit. Please try again tomorrow.");

    IamErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}