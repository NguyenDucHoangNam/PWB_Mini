package com.pwb.backend.exception;

public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
