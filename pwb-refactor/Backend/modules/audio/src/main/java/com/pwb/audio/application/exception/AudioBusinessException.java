package com.pwb.audio.application.exception;

import com.pwb.shared.exception.ErrorCode;

public class AudioBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public AudioBusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public AudioBusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AudioBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
