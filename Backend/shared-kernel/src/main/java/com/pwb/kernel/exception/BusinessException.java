package com.pwb.kernel.exception;

public class BusinessException extends BaseBusinessException {

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
