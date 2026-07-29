package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.RateLimiter;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RateLimiter rateLimiter;
    private final LoginAttemptChecker attemptChecker;
    private final TokenService tokenService;
    private final RefreshTokenManager refreshTokenManager;
    private final AuthEventPublisher authEventPublisher;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public LoginResult execute(LoginCommand command) {
        String email = command.email().trim().toLowerCase();
        String clientIp = command.clientIp() == null ? "unknown" : command.clientIp();

        enforceRateLimit("login:ip:" + clientIp, loginPolicy.loginPerMinute());
        enforceRateLimit("login:email:" + email, loginPolicy.loginPerMinute());

        LoginAttemptChecker.LockState lockState = attemptChecker.isLocked(email, clientIp);
        if (lockState.locked()) {
            log.warn("Login rejected - account locked: email={} ip={} retryAfter={}", email, clientIp, lockState.retryAfterSeconds());
            authEventPublisher.publishLoginFailed(email, clientIp, "ACCOUNT_LOCKED");
            throw new BusinessException(IamErrorCode.ACCOUNT_LOCKED,
                    java.util.Map.of("retryAfterSeconds", lockState.retryAfterSeconds()));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            attemptChecker.recordFailure(email, clientIp);
            authEventPublisher.publishLoginFailed(email, clientIp, "USER_NOT_FOUND");
            throw new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS);
        }

        if (user.getPassword() == null || !passwordHasher.matches(command.rawPassword(), user.getPassword().hash())) {
            attemptChecker.recordFailure(email, clientIp);
            log.warn("Login failed - bad credentials: email={}", email);
            authEventPublisher.publishLoginFailed(email, clientIp, "BAD_CREDENTIALS");
            throw new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            authEventPublisher.publishLoginFailed(email, clientIp, "ACCOUNT_INACTIVE");
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            authEventPublisher.publishLoginFailed(email, clientIp, "ACCOUNT_NOT_VERIFIED");
            throw new BusinessException(IamErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        attemptChecker.reset(email);

        TokenService.AccessToken access = tokenService.issueAccessToken(user);
        RefreshTokenManager.RefreshToken refresh = refreshTokenManager.issue(user.getUserId());

        authEventPublisher.publishAuthSuccess(user.getUserId(), user.getEmail().value(), clientIp);

        AuthNextStep nextStep = user.isOnboardingIncomplete() ? AuthNextStep.COMPLETE_PROFILE : AuthNextStep.NONE;

        log.info("Login success: userId={} nextStep={}", user.getUserId(), nextStep);
        return new LoginResult(user, access, refresh, nextStep);
    }

    private void enforceRateLimit(String key, int limit) {
        RateLimiter.Decision decision = rateLimiter.consume(key, limit, Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }
    }
}