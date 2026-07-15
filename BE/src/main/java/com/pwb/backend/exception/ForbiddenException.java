package com.pwb.backend.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.AUTH_FORBIDDEN);
    }

    public ForbiddenException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
