package com.pwb.infra.storage.exception;

import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCode;
import lombok.Getter;

@Getter
public class StorageException extends BusinessException {

    public StorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public StorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}