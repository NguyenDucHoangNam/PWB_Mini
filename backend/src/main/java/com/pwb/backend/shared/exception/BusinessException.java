package com.pwb.backend.shared.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeLike errorCode;
    private final Object[] args;
    private final Object data;

    public BusinessException(ErrorCodeLike errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, Object[] args) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    public BusinessException(ErrorCodeLike errorCode, String message, Object[] args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
        this.data = null;
    }

    @Deprecated
    public BusinessException(ErrorCodeLike errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
        this.data = data;
    }
}