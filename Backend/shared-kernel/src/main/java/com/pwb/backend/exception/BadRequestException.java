package com.pwb.backend.exception;

public class BadRequestException extends BaseBusinessException {

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
