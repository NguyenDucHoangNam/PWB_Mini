package com.pwb.backend.exception;

public class BusinessException extends BaseBusinessException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }
}
