package com.pwb.backend.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName, fieldName, fieldValue);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
