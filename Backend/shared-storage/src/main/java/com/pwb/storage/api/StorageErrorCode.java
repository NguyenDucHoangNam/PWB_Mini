package com.pwb.storage.api;

import com.pwb.backend.exception.ErrorCode;

public enum StorageErrorCode implements ErrorCode {

    STORAGE_UPLOAD_FAILED   ("STORAGE_001", "Storage upload failed.",         500),
    STORAGE_DOWNLOAD_FAILED ("STORAGE_002", "Storage download failed.",       500),
    STORAGE_OBJECT_NOT_FOUND("STORAGE_003", "Storage object not found.",      404),
    STORAGE_DELETE_FAILED   ("STORAGE_004", "Storage delete failed.",         500),
    STORAGE_PRESIGN_FAILED  ("STORAGE_005", "Storage presign URL failed.",    500),
    STORAGE_INVALID_KEY     ("STORAGE_006", "Invalid storage key format.",    400);

    StorageErrorCode(String code, String message, int httpStatus) {
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
