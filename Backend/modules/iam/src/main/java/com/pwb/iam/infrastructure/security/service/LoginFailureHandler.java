package com.pwb.iam.infrastructure.security.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public void recordFailureAndTranslate(String email, String ip) {
        loginAttemptService.recordFailure(email, ip);

        if (loginAttemptService.isEmailLocked(email)) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (loginAttemptService.isIpLocked(ip)) {
            throw new BusinessException(ErrorCode.AUTH_IP_LOCKED);
        }
        throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
    }
}
