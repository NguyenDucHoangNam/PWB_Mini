package com.pwb.iam.application.usecase.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.domain.exception.IamErrorCode;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.RefreshTokenManager;
import com.pwb.iam.domain.service.TokenResult;
import com.pwb.iam.domain.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final TokenService tokenService;

    @Override
    @Transactional
    public TokenResult execute(RefreshTokenCommand command) {
        if (!tokenService.validateRefreshToken(command.refreshToken())) {
            throw new BusinessException(IamErrorCode.AUTH_TOKEN_INVALID);
        }

        String jti;
        try {
            jti = tokenService.extractJtiFromRefreshToken(command.refreshToken());
        } catch (Exception ex) {
            throw new BusinessException(IamErrorCode.AUTH_TOKEN_INVALID);
        }
        if (jti == null || jti.isBlank()) {
            throw new BusinessException(IamErrorCode.AUTH_TOKEN_INVALID);
        }

        UUID userId = refreshTokenManager.findUserIdByJti(jti).orElse(null);
        if (userId == null) {
            throw new BusinessException(IamErrorCode.AUTH_TOKEN_INVALID);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        refreshTokenManager.revoke(jti);

        log.info("Token refreshed: userId={}", user.getUserId());
        return tokenService.buildAuthTokens(user, TokenResult.NextStep.NONE);
    }
}
