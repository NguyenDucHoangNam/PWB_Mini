package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RateLimitGuard rateLimitGuard;
    private final LoginAttemptChecker attemptChecker;
    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public LoginResult execute(LoginCommand command) {
        String email = EmailAddress.normalize(command.email());
        String clientIp = command.clientIp();
        String userAgent = command.userAgent();

        rateLimitGuard.checkIpAndSubject("login", clientIp, email, loginPolicy.loginPerMinute());

        LoginAttemptChecker.LockState lockState = attemptChecker.isLocked(email, clientIp);
        if (lockState.locked()) {
            log.warn("Login rejected - account locked: email={} ip={} retryAfter={}",
                    email, clientIp, lockState.retryAfterSeconds());
            authEventPublisher.publishLoginFailed(email, clientIp, userAgent, "ACCOUNT_LOCKED");
            throw new BusinessException(IamErrorCode.ACCOUNT_LOCKED,
                    Map.of("retryAfterSeconds", lockState.retryAfterSeconds()));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getPassword() == null
                || !passwordHasher.matches(command.rawPassword(), user.getPassword().hash())) {
            attemptChecker.recordFailure(email, clientIp);
            log.warn("Login failed - bad credentials: email={}", email);
            authEventPublisher.publishLoginFailed(email, clientIp, userAgent, "BAD_CREDENTIALS");
            throw new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS);
        }

        // Status is only inspected once the password has been proven, so an attacker cannot
        // probe which addresses are registered, banned or unverified without the credentials.
        if (user.isBlocked()) {
            authEventPublisher.publishLoginFailed(email, clientIp, userAgent, "ACCOUNT_INACTIVE");
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            authEventPublisher.publishLoginFailed(email, clientIp, userAgent, "ACCOUNT_NOT_VERIFIED");
            throw new BusinessException(IamErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        attemptChecker.reset(email);
        attemptChecker.resetIpLock(clientIp);

        TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(user);
        TokenManagerService.RefreshTokenInfo refresh = tokenManagerService.issueRefreshToken(user.getUserId());

        authEventPublisher.publishAuthSuccess(
                AuthSuccessEvent.of(user.getUserId(), user.getEmail().value(), clientIp, userAgent));

        log.info("Login success: userId={}", user.getUserId());
        return new LoginResult(user, access, refresh);
    }
}
