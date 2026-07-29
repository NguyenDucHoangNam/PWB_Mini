package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.service.TokenManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final TokenManagerService tokenManagerService;
    private final AuthEventPublisher authEventPublisher;

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        if (command.rawRefreshToken() != null && !command.rawRefreshToken().isBlank()) {
            tokenManagerService.revokeRefreshToken(command.rawRefreshToken());
        }
        if (command.accessJti() != null && !command.accessJti().isBlank() && command.accessExpiresInSeconds() > 0) {
            tokenManagerService.blacklistAccessToken(command.accessJti(), command.accessExpiresInSeconds());
        }
        authEventPublisher.publishLogout(command.userId(), null, null);
    }
}