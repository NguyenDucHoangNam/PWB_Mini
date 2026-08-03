package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.BusinessException;

public class RefreshTokenInvalidException extends BusinessException {

    public RefreshTokenInvalidException() {
        super(IamErrorCode.REFRESH_TOKEN_INVALID);
    }

    public RefreshTokenInvalidException(String message) {
        super(IamErrorCode.REFRESH_TOKEN_INVALID, message);
    }
}
