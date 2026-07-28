package com.pwb.iam.infrastructure.security.service;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.service.LoginAttemptChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptCheckerAdapter implements LoginAttemptChecker {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void recordFailure(String email, String ip) {
        loginAttemptService.recordFailure(email, ip);
    }

    @Override
    public void recordSuccess(String email, String ip) {
        loginAttemptService.recordSuccess(email, ip);
    }

    @Override
    public boolean isEmailLocked(String email) {
        return loginAttemptService.isEmailLocked(email);
    }

    @Override
    public boolean isIpLocked(String ip) {
        return loginAttemptService.isIpLocked(ip);
    }

    @Override
    public void handleFailureAndThrow(String email, String ip) {
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
