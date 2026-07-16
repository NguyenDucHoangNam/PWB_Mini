package com.pwb.backend.exception;

import lombok.Getter;

@Getter
public abstract class BaseBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseBusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
