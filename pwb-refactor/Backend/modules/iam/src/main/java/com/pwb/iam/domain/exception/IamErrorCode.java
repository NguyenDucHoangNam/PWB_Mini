package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IamErrorCode implements ErrorCode {

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "IAM_001", "Email already registered."),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "IAM_002", "Password is too weak."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "IAM_003", "User not found."),
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
