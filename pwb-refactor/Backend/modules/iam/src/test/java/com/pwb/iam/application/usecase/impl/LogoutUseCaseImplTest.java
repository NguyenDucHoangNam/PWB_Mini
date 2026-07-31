package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.testsupport.StubTokenManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseImplTest {

    @Mock private AuthEventPublisher authEventPublisher;

    private TokenManagerService tokenManagerService;
    private LogoutUseCaseImpl useCase;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tokenManagerService = new StubTokenManagerService(userId);
        useCase = new LogoutUseCaseImpl(tokenManagerService, authEventPublisher);
    }

    @Test
    @DisplayName("should revoke refresh token and blacklist access when both provided")
    void should_revoke_and_blacklist() {
        useCase.execute(new LogoutCommand(userId, "raw-refresh", "jti-1", 900L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "ua");
    }

    @Test
    @DisplayName("should skip blacklist when accessExpiresInSeconds non-positive")
    void should_skip_blacklist_when_no_jti() {
        useCase.execute(new LogoutCommand(userId, "raw-refresh", null, 0L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "ua");
    }

    @Test
    @DisplayName("should skip refresh token revocation when blank")
    void should_skip_refresh_revoke() {
        useCase.execute(new LogoutCommand(userId, "", null, 0L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "ua");
    }

    @Test
    @DisplayName("should skip both when refresh and access missing")
    void should_skip_when_both_missing() {
        useCase.execute(new LogoutCommand(userId, null, null, 0L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "ua");
    }

    @Test
    @DisplayName("should publish logout even when userId is null (best effort)")
    void should_publish_logout_without_user() {
        useCase.execute(new LogoutCommand(null, "raw", "jti", 900L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(null, "10.0.0.1", "ua");
    }

    @Test
    @DisplayName("should call publishLogout overload with userAgent param")
    void should_publish_logout_with_user_agent() {
        useCase.execute(new LogoutCommand(userId, "raw", "jti", 900L, "10.0.0.1", "agent-x"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "agent-x");
    }

    @Test
    @DisplayName("should ignore blank jti and not blacklist")
    void should_ignore_blank_jti() {
        useCase.execute(new LogoutCommand(userId, "raw", "  ", 900L, "10.0.0.1", "ua"));

        verify(authEventPublisher).publishLogout(userId, "10.0.0.1", "ua");
        verify(authEventPublisher, never()).publishLogout(userId, null, "ua");
    }
}