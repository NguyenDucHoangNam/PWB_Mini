package com.pwb.backend.exception;

public class BusinessException extends BaseBusinessException {

    private final Object[] args;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
        this.args = new Object[0];
    }

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode);
        this.args = args == null ? new Object[0] : args;
    }

    public Object[] getArgs() {
        return args;
    }
}
