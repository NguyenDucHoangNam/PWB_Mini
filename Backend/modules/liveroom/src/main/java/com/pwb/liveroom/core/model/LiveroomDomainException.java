package com.pwb.liveroom.core.model;

public class LiveroomDomainException extends RuntimeException {

    private final String errorKey;

    public LiveroomDomainException(String errorKey, String message) {
        super(message);
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
