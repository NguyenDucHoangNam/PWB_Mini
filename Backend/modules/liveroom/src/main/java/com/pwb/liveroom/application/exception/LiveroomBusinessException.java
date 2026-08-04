package com.pwb.liveroom.application.exception;

import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ErrorCode;

import java.util.Map;

public class LiveroomBusinessException extends BusinessException {

    public LiveroomBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LiveroomBusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public LiveroomBusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }


    public LiveroomBusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, details);
    }
}