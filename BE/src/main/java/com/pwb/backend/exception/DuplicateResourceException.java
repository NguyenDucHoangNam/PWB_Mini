package com.pwb.backend.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resourceName) {
        super(ErrorCode.RESOURCE_DUPLICATE, resourceName);
    }

    public DuplicateResourceException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
