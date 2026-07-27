package com.pwb.backend.exception;

public class BadRequestException extends BaseBusinessException {

    public BadRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
