package com.pwb.infra.storage.exception;

import com.pwb.shared.exception.ErrorCategory;
import com.pwb.shared.exception.ErrorCode;

public enum StorageErrorCode implements ErrorCode {

    STORAGE_UPLOAD_FAILED    (ErrorCategory.INTERNAL,   "STORAGE_001", "Storage upload failed."),
    STORAGE_DOWNLOAD_FAILED  (ErrorCategory.INTERNAL,   "STORAGE_002", "Storage download failed."),
    STORAGE_OBJECT_NOT_FOUND (ErrorCategory.NOT_FOUND,  "STORAGE_003", "Storage object not found."),
    STORAGE_DELETE_FAILED    (ErrorCategory.INTERNAL,   "STORAGE_004", "Storage delete failed."),
    STORAGE_PRESIGN_FAILED   (ErrorCategory.INTERNAL,   "STORAGE_005", "Storage presign URL failed."),
    STORAGE_INVALID_KEY      (ErrorCategory.VALIDATION, "STORAGE_006", "Invalid storage key format.");

    private final ErrorCategory category;
    private final String code;
    private final String message;

    StorageErrorCode(ErrorCategory category, String code, String message) {
        this.category = category;
        this.code = code;
        this.message = message;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}