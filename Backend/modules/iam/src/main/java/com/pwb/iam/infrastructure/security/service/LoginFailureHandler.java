package com.pwb.iam.infrastructure.security.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.iam.core.exception.IamErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public void recordFailureAndTranslate(String email, String ip) {
        loginAttemptService.recordFailure(email, ip);

        if (loginAttemptService.isEmailLocked(email)) {
            throw new BusinessException(IamErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (loginAttemptService.isIpLocked(ip)) {
            throw new BusinessException(IamErrorCode.AUTH_IP_LOCKED);
        }
        throw new BusinessException(IamErrorCode.AUTH_LOGIN_FAILED);
    }
}



