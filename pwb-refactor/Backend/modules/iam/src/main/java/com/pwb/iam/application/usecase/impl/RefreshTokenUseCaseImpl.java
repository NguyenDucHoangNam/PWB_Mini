package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.RefreshTokenExpiredException;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
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
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final RefreshTokenManager refreshTokenManager;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final AuthEventPublisher authEventPublisher;
    private final LoginPolicy loginPolicy;

    @Override
    @Transactional
    public LoginResult execute(RefreshTokenCommand command) {
        String clientIp = command.clientIp() == null ? "unknown" : command.clientIp();
        RateLimiter.Decision decision = rateLimiter.consume(
                "refresh:ip:" + clientIp, loginPolicy.refreshPerMinute(), Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }

        RefreshTokenManager.RefreshToken rotated;
        try {
            rotated = refreshTokenManager.rotate(command.rawRefreshToken());
        } catch (RefreshTokenExpiredException | RefreshTokenInvalidException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RefreshTokenInvalidException(ex.getMessage());
        }

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new RefreshTokenInvalidException("User not found for token"));

        TokenService.AccessToken access = tokenService.issueAccessToken(user);
        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value()));
        log.info("Refresh token rotated: userId={}", user.getUserId());
        return new LoginResult(user, access, rotated);
    }
}