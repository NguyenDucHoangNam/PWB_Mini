package com.pwb.backend.exception;

public enum WsErrorCode implements ErrorCode {

    WS_NOT_PARTICIPANT     ("WS_001", "User is not an active participant of this room.",      403),
    WS_TARGET_NOT_PARTICIPANT("WS_002", "Target user is not an active participant of this room.", 403);

    WsErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    private final String code;
    private final String message;
    private final int httpStatus;

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
