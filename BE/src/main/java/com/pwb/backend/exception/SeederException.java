package com.pwb.backend.exception;

public class SeederException extends BusinessException {

    public SeederException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public SeederException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}