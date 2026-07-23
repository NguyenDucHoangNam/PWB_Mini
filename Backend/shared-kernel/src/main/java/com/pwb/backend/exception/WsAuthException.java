package com.pwb.backend.exception;

public class WsAuthException extends RuntimeException {

    private final String code;

    public WsAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
