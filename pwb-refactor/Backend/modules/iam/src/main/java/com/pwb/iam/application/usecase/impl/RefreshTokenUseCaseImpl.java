package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.RateLimiter;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenService;
import com.pwb.iam.infrastructure.config.RateLimitProperties;
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
    private final RateLimitProperties rateLimitProperties;

    @Override
    @Transactional
    public LoginResult execute(RefreshTokenCommand command) {
        String clientIp = command.clientIp() == null ? "unknown" : command.clientIp();
        RateLimiter.Decision decision = rateLimiter.consume(
                "refresh:ip:" + clientIp, rateLimitProperties.getRefreshPerMinute(), Duration.ofMinutes(1));
        if (!decision.allowed()) {
            throw new BusinessException(IamErrorCode.RATE_LIMITED,
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }

        RefreshTokenManager.RefreshToken rotated;
        try {
            rotated = refreshTokenManager.rotate(command.rawRefreshToken());
        } catch (IllegalStateException ex) {
            switch (ex.getMessage() == null ? "" : ex.getMessage()) {
                case "REFRESH_TOKEN_EXPIRED" -> throw new BusinessException(IamErrorCode.REFRESH_TOKEN_EXPIRED);
                case "REFRESH_TOKEN_INVALID" -> throw new BusinessException(IamErrorCode.REFRESH_TOKEN_INVALID);
                default -> throw new BusinessException(IamErrorCode.REFRESH_TOKEN_INVALID);
            }
        }

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        TokenService.AccessToken access = tokenService.issueAccessToken(user);
        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value()));
        log.info("Refresh token rotated: userId={}", user.getUserId());
        return new LoginResult(access, rotated);
    }
}