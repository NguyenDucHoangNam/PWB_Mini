package com.pwb.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum CommonErrorCode implements ErrorCode {

    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "HTTP method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT("CONFLICT", "Resource state conflict", HttpStatus.CONFLICT),
    RATE_LIMITED("RATE_LIMITED", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "Quota exceeded", HttpStatus.TOO_MANY_REQUESTS),
    RESOURCE_BUSY("RESOURCE_BUSY", "Resource is busy", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Idempotency key conflict", HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "Request payload too large", HttpStatus.PAYLOAD_TOO_LARGE),
    EXTERNAL_SERVICE_FAILURE("EXTERNAL_SERVICE_FAILURE", "Upstream service failure", HttpStatus.BAD_GATEWAY),
    UPSTREAM_TIMEOUT("UPSTREAM_TIMEOUT", "Upstream service timeout", HttpStatus.GATEWAY_TIMEOUT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}