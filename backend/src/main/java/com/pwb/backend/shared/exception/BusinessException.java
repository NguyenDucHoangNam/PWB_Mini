package com.pwb.backend.shared.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] args;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, Object[] args) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object[] args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = data;
    }
}