package com.pwb.backend.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.defaultMessage() : null);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode != null ? errorCode.defaultMessage() : null, cause);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null || details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(Map.copyOf(details));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public HttpStatus httpStatus() {
        return errorCode != null ? errorCode.httpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public String errorCodeName() {
        return errorCode instanceof Enum<?> enumCode ? enumCode.name() : errorCode.code();
    }
}