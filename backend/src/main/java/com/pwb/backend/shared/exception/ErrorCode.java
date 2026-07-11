package com.pwb.backend.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements ErrorCodeLike {

    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            HttpStatus.INTERNAL_SERVER_ERROR),

    VALIDATION_FAILED("VALIDATION_FAILED",
            "Validation failed for request parameters",
            HttpStatus.BAD_REQUEST),

    UNAUTHORIZED("UNAUTHORIZED",
            "Full authentication is required to access this resource",
            HttpStatus.UNAUTHORIZED),

    FORBIDDEN("FORBIDDEN",
            "You do not have permission to access this resource",
            HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND",
            "Requested resource was not found",
            HttpStatus.NOT_FOUND),

    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED",
            "Too many requests, please try again later",
            HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}