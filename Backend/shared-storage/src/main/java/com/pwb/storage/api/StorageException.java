package com.pwb.storage.api;

import com.pwb.backend.exception.BaseBusinessException;
import com.pwb.backend.exception.ErrorCode;
import lombok.Getter;

@Getter
public class StorageException extends BaseBusinessException {

    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public StorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
