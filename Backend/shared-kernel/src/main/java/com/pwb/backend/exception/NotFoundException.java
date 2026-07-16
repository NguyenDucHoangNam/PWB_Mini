package com.pwb.backend.exception;

public class NotFoundException extends BaseBusinessException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
