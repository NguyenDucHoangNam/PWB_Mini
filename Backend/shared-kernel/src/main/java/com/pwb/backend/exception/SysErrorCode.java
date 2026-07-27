package com.pwb.backend.exception;

public enum SysErrorCode implements ErrorCode {

    INTERNAL_SERVER_ERROR("SYS_000", "Internal server error.", 500),
    INVALID_INPUT         ("SYS_001", "Invalid input.", 400),
    UNAUTHORIZED          ("SYS_002", "Authentication required.", 401),
    FORBIDDEN             ("SYS_003", "Access denied.", 403),
    RESOURCE_NOT_FOUND    ("SYS_004", "Resource not found.", 404),
    FILE_TOO_LARGE        ("SYS_005", "File size exceeds maximum allowed.", 413);

    SysErrorCode(String code, String message, int httpStatus) {
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
