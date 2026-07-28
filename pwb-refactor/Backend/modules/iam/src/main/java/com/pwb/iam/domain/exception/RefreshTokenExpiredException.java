package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.BusinessException;

public class RefreshTokenExpiredException extends BusinessException {

    public RefreshTokenExpiredException() {
        super(IamErrorCode.REFRESH_TOKEN_EXPIRED);
    }
}
