package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.BusinessException;

import java.util.Map;

public class OtpVerificationException extends BusinessException {

    public OtpVerificationException(IamErrorCode errorCode) {
        super(errorCode);
    }

    public OtpVerificationException(IamErrorCode errorCode, Map<String, Object> details) {
        super(errorCode, errorCode.defaultMessage(), details);
    }
}
