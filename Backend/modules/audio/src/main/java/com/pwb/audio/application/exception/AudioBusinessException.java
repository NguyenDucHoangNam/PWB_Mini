package com.pwb.audio.application.exception;

import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCode;

public class AudioBusinessException extends BusinessException {

    public AudioBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AudioBusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AudioBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
