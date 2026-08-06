package com.pwb.shared.exception;

public enum SysErrorCode implements ErrorCode {
    FILE_TOO_LARGE            (ErrorCategory.VALIDATION,         "FILE_TOO_LARGE",            "Uploaded file exceeds the maximum allowed size"),
    INVALID_REQUEST           (ErrorCategory.VALIDATION,         "INVALID_REQUEST",           "The request is invalid"),
    INVALID_PARAMETER         (ErrorCategory.VALIDATION,         "INVALID_PARAMETER",         "One or more request parameters are invalid"),
    MALFORMED_REQUEST_BODY    (ErrorCategory.VALIDATION,         "MALFORMED_REQUEST_BODY",    "The request body could not be parsed"),
    ACCESS_DENIED             (ErrorCategory.FORBIDDEN,          "ACCESS_DENIED",             "You do not have permission to perform this action"),
    RESOURCE_NOT_FOUND        (ErrorCategory.NOT_FOUND,          "RESOURCE_NOT_FOUND",        "The requested resource was not found"),
    METHOD_NOT_ALLOWED        (ErrorCategory.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",        "The HTTP method is not supported for this endpoint"),
    CONCURRENT_UPDATE         (ErrorCategory.CONFLICT,           "CONCURRENT_UPDATE",         "The resource was modified by another request. Please retry."),
    DATA_INTEGRITY_VIOLATION  (ErrorCategory.CONFLICT,           "DATA_INTEGRITY_VIOLATION",  "Data integrity violation occurred"),
    INTERNAL_SERVER_ERROR     (ErrorCategory.INTERNAL,           "INTERNAL_SERVER_ERROR",     "An unexpected error occurred");

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