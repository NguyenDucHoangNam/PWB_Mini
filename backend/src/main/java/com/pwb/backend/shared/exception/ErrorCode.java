package com.pwb.backend.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed for request parameters", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Full authentication is required to access this resource", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Requested resource was not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}

