package com.pwb.backend.exception;

public class WsAuthException extends BaseBusinessException {

    public WsAuthException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
