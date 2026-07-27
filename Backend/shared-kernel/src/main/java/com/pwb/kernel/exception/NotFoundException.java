package com.pwb.kernel.exception;

public class NotFoundException extends BaseBusinessException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
