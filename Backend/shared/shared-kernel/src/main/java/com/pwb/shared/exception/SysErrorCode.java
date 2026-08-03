package com.pwb.shared.exception;

public enum SysErrorCode implements ErrorCode {
    FILE_TOO_LARGE         (ErrorCategory.VALIDATION, "FILE_TOO_LARGE",          "Uploaded file exceeds the maximum allowed size"),
    RESOURCE_NOT_FOUND     (ErrorCategory.NOT_FOUND,   "RESOURCE_NOT_FOUND",      "The requested resource was not found"),
    INTERNAL_SERVER_ERROR  (ErrorCategory.INTERNAL,    "INTERNAL_SERVER_ERROR",   "An unexpected error occurred");

    private final ErrorCategory category;
    private final String code;
    private final String defaultMessage;

    SysErrorCode(ErrorCategory category, String code, String defaultMessage) {
        this.category = category;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}