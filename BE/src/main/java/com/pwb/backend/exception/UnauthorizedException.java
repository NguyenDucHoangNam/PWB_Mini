package com.pwb.backend.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.AUTH_UNAUTHORIZED);
    }

    public UnauthorizedException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
