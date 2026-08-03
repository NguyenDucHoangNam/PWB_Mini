package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.BusinessException;

public class UserStateConflictException extends BusinessException {

    public UserStateConflictException(IamErrorCode errorCode) {
        super(errorCode);
    }
}
