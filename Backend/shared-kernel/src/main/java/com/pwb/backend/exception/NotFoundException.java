package com.pwb.backend.exception;

public class NotFoundException extends BaseBusinessException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
