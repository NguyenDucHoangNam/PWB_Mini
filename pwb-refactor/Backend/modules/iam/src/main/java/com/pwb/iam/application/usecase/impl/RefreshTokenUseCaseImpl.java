package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final TokenManagerService tokenManagerService;
    private final UserRepository userRepository;
    private final ThrottlingService throttlingService;
    private final AuthEventPublisher authEventPublisher;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public LoginResult execute(RefreshTokenCommand command) {
        String clientIp = command.clientIp() == null ? "unknown" : command.clientIp();
        ThrottlingService.ThrottleDecision decision = throttlingService.consume(
                "refresh:ip:" + clientIp, loginPolicy.refreshPerMinute(), Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }

        TokenManagerService.RefreshTokenInfo rotated;
        rotated = tokenManagerService.rotateRefreshToken(command.rawRefreshToken());

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new RefreshTokenInvalidException("User not found for token"));

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_INACTIVE);
        }

        TokenManagerService.AccessTokenInfo access = tokenManagerService.issueAccessToken(user);
        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value(), clientIp, null));
        AuthNextStep nextStep = user.isOnboardingIncomplete() ? AuthNextStep.COMPLETE_PROFILE : AuthNextStep.NONE;
        log.info("Refresh token rotated: userId={} nextStep={}", user.getUserId(), nextStep);
        return new LoginResult(user, access, rotated, nextStep);
    }
}