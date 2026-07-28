package com.pwb.shared.exception;

import lombok.Getter;
import java.util.Map;
import java.util.Optional;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(cause.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.details = Optional.ofNullable(details).orElse(Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = Optional.ofNullable(details).orElse(Map.of());
    }

    public String getCode() {
        return errorCode.code();
    }

    public int getHttpStatus() {
        return errorCode.httpStatus().value();
    }
}
