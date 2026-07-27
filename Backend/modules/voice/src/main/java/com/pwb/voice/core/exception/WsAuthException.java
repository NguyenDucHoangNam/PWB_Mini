package com.pwb.voice.core.exception;

import com.pwb.kernel.exception.BaseBusinessException;
import com.pwb.kernel.exception.ErrorCode;

public class WsAuthException extends BaseBusinessException {

    public WsAuthException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
