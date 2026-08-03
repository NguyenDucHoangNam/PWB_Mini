package com.pwb.iam.domain.exception;

import com.pwb.shared.exception.BusinessException;

import java.util.Map;

public class WeakPasswordException extends BusinessException {

    public WeakPasswordException(String reasons) {
        super(IamErrorCode.WEAK_PASSWORD,
                IamErrorCode.WEAK_PASSWORD.defaultMessage(),
                Map.of("reasons", reasons == null ? "" : reasons));
    }
}
