package com.pwb.shared.exception;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum SysErrorCode implements ErrorCode {
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the maximum allowed size"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The requested resource was not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred");

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
