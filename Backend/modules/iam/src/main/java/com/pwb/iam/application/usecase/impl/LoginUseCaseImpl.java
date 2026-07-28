package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.infrastructure.security.util.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCaseImpl implements LoginUseCase {

    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptChecker loginAttemptChecker;
    private final TokenService tokenService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public TokenResult execute(LoginCommand command) {
        String email = command.usernameOrEmail().trim().toLowerCase();
        String clientIp = ClientIpResolver.getClientIp();

        if (loginAttemptChecker.isEmailLocked(email)) {
            throw new BusinessException(IamErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (loginAttemptChecker.isIpLocked(clientIp)) {
            throw new BusinessException(IamErrorCode.AUTH_IP_LOCKED);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        String storedHash = user != null ? user.getPassword().getHash() : DUMMY_BCRYPT_HASH;

        if (user == null || !passwordHasher.matches(command.password(), storedHash)) {
            loginAttemptChecker.handleFailureAndThrow(email, clientIp);
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(IamErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            loginAttemptChecker.handleFailureAndThrow(email, clientIp);
        }

        loginAttemptChecker.recordSuccess(email, clientIp);
        authEventPublisher.publishLoginSuccess(user.getUserId(), user.getEmail().value());
        log.info("User login success: userId={}", user.getUserId());
        return tokenService.buildAuthTokens(user, TokenResult.NextStep.NONE);
    }
}
