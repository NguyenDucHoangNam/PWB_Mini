package com.pwb.infra.storage.exception;

import com.pwb.shared.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum StorageErrorCode implements ErrorCode {

    STORAGE_UPLOAD_FAILED   ("STORAGE_001", "Storage upload failed.",         HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_DOWNLOAD_FAILED ("STORAGE_002", "Storage download failed.",       HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_OBJECT_NOT_FOUND("STORAGE_003", "Storage object not found.",      HttpStatus.NOT_FOUND),
    STORAGE_DELETE_FAILED   ("STORAGE_004", "Storage delete failed.",         HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_PRESIGN_FAILED  ("STORAGE_005", "Storage presign URL failed.",    HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_INVALID_KEY     ("STORAGE_006", "Invalid storage key format.",    HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    StorageErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}