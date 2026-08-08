package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.RefreshTokenInvalidException;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubTokenManagerService;
import com.pwb.iam.testsupport.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;

    private TokenManagerService tokenManagerService;
    private LoginPolicy loginPolicy;
    private RefreshTokenUseCaseImpl useCase;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tokenManagerService = new StubTokenManagerService(userId);
        loginPolicy = new LoginPolicy(10, 30, 10, 5, 10, 5, 5, 15);
        lenient().when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));

        useCase = new RefreshTokenUseCaseImpl(tokenManagerService, userRepository,
                new RateLimitGuard(throttlingService), authEventPublisher, loginPolicy);
    }

    @Test
    @DisplayName("should rotate refresh token and return new tokens")
    void should_rotate_and_return_tokens() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var result = useCase.execute(new RefreshTokenCommand("raw-refresh-token", "10.0.0.1"));

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken().rawToken()).isEqualTo("refresh-token-stub");
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when throttled")
    void should_throw_rate_limited() {
        when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.deny(60L));

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenCommand("raw", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should throw RefreshTokenInvalidException when token rotation fails")
    void should_throw_when_token_invalid() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenCommand("raw", "10.0.0.1")))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when user BANNED")
    void should_throw_when_user_banned() {
        User user = TestUserBuilder.banned();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenCommand("raw", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when user DELETED")
    void should_throw_when_user_deleted() {
        User user = TestUserBuilder.deleted();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenCommand("raw", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("should default null clientIp to 'unknown'")
    void should_handle_null_client_ip() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var result = useCase.execute(new RefreshTokenCommand("raw", null));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should reject blank raw refresh token at command boundary")
    void should_reject_blank_token() {
        assertThatThrownBy(() -> new RefreshTokenCommand("", "ip"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenCommand(null, "ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}