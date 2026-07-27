package com.pwb.kernel.exception;

import lombok.Getter;

@Getter
public abstract class BaseBusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    protected BaseBusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args;
    }
}
