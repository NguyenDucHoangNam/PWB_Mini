package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.domain.service.AccessTokenBlacklist;
import com.pwb.iam.domain.service.RefreshTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final RefreshTokenManager refreshTokenManager;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        if (command.rawRefreshToken() != null && !command.rawRefreshToken().isBlank()) {
            refreshTokenManager.revoke(command.rawRefreshToken());
        }
        if (command.accessJti() != null && !command.accessJti().isBlank() && command.accessExpiresInSeconds() > 0) {
            accessTokenBlacklist.blacklist(command.accessJti(), command.accessExpiresInSeconds());
        }
        log.info("Logout: userId={}", command.userId());
    }
}