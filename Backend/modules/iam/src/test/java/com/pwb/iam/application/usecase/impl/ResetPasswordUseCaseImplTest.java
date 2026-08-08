package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.application.service.AccountNotifier;
import com.pwb.iam.application.service.PasswordHistoryGuard;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubPasswordHasher;
import com.pwb.iam.testsupport.StubPasswordResetTokenService;
import com.pwb.iam.testsupport.StubTokenManagerService;
import com.pwb.iam.testsupport.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private EmailDeliveryPort emailDeliveryPort;
    @Mock private ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private ThrottlingService throttlingService;

    private StubPasswordHasher passwordHasher;
    private StubPasswordResetTokenService passwordResetTokenService;
    private StubTokenManagerService tokenManagerService;
    private ResetPasswordUseCaseImpl useCase;
    private UUID userId;
    private String signedToken;
    private String rawToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        passwordHasher = new StubPasswordHasher();
        passwordResetTokenService = new StubPasswordResetTokenService();
        tokenManagerService = new StubTokenManagerService(userId);
        signedToken = passwordResetTokenService.generateSignedToken();
        rawToken = passwordResetTokenService.extractRawToken(signedToken);

        // Reset is now rate limited per client address before the token is even looked at, so the
        // use case needs a guard. Allowing every call here keeps these tests about the reset flow;
        // the rejection path is covered at the HTTP boundary in AuthControllerTest.
        lenient().when(throttlingService.consume(anyString(), anyInt(), any()))
                .thenReturn(ThrottlingService.ThrottleDecision.allow(5L));

        useCase = new ResetPasswordUseCaseImpl(
                userRepository, passwordResetTokenRepository, passwordResetTokenService,
                new PasswordHistoryGuard(passwordHistoryRepository, passwordHasher),
                new AccountNotifier(emailDeliveryPort),
                passwordHasher, validatePasswordPolicyUseCase, tokenManagerService,
                authEventPublisher, new RateLimitGuard(throttlingService),
                new LoginPolicy(10, 30, 10, 5, 10, 5, 5, 15));
    }

    @Test
    @DisplayName("should reset password and revoke all refresh tokens for user")
    void should_reset_password() {
        User user = TestUserBuilder.withUserId(userId);
        PasswordResetToken token = PasswordResetToken.create(userId, "some-hash",
                Instant.now().plus(30, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryRepository.countByUserId(userId)).thenReturn(0L);

        var result = useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua"));

        assertThat(result.userId()).isEqualTo(userId);
        // Not invalidateAllForUser: outstanding tokens are cleared when a new one is *issued*
        // (see ForgotPasswordUseCaseImpl). Redeeming one burns that token alone, via markUsed —
        // asserted in `should mark token used after successful reset`.
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        // "unknown", not null: the short ResetPasswordCommand constructor fills in a missing address
        // the same way LoginCommand does, so the audit trail never carries a null key.
        verify(authEventPublisher).publishPasswordChanged(
                eq(userId), eq(user.getEmail().value()), eq("unknown"), eq("ua"));
        // Whoever requested the reset may not be who is still signed in, so every existing session
        // goes — that is the point of the reset, not a side effect.
        assertThat(tokenManagerService.revokedAllForUsers()).containsExactly(userId);
    }

    @Test
    @DisplayName("should throw AUTH_RESET_TOKEN_INVALID when signature invalid")
    void should_throw_when_signature_invalid() {
        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand("bad.signature", "NewPass123!@#", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_RESET_TOKEN_INVALID");
    }

    @Test
    @DisplayName("should throw AUTH_RESET_TOKEN_INVALID when token not found in repository")
    void should_throw_when_token_not_in_db() {
        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_RESET_TOKEN_INVALID");
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user missing")
    void should_throw_when_user_missing() {
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));
        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("should throw AUTH_OAUTH_USER_NO_PASSWORD for OAuth user")
    void should_throw_when_oauth_user() {
        User user = TestUserBuilder.googleActive();
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OAUTH_USER_NO_PASSWORD");
    }

    @Test
    @DisplayName("should throw AUTH_PASSWORD_RECENTLY_USED when new password matches history")
    void should_throw_when_password_recently_used() {
        User user = TestUserBuilder.withUserId(userId);
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

        PasswordHistory recent = PasswordHistory.create(userId, passwordHasher.hash("NewPass123!@#"));

        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt()))
                .thenReturn(List.of(recent));

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_PASSWORD_RECENTLY_USED");
    }

    @Test
    @DisplayName("should propagate WEAK_PASSWORD from validator")
    void should_propagate_weak_password() {
        User user = TestUserBuilder.withUserId(userId);
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

        // No history stub: policy runs before the reuse guard, so a weak password never reaches it.
        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new BusinessException(IamErrorCode.WEAK_PASSWORD))
                .when(validatePasswordPolicyUseCase).validate("weak");

        assertThatThrownBy(() -> useCase.execute(new ResetPasswordCommand(signedToken, "weak", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("should mark token used after successful reset")
    void should_mark_token_used() {
        User user = TestUserBuilder.withUserId(userId);
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryRepository.countByUserId(userId)).thenReturn(0L);
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua"));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
    }

    @Test
    @DisplayName("should delete oldest password history entries when count exceeds MAX_HISTORY_SIZE")
    void should_delete_oldest_history_when_exceeds() {
        User user = TestUserBuilder.withUserId(userId);
        PasswordResetToken token = PasswordResetToken.create(userId, "hash", Instant.now().plus(30, ChronoUnit.MINUTES));

        when(passwordResetTokenRepository.findActiveByHash(any(), any())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryRepository.countByUserId(userId))
                .thenReturn((long) (PasswordHistory.MAX_HISTORY_SIZE + 2));

        useCase.execute(new ResetPasswordCommand(signedToken, "NewPass123!@#", "ua"));

        verify(passwordHistoryRepository).deleteOldestByUserId(eq(userId), eq(2));
    }

    @Test
    @DisplayName("should reject blank token at command boundary")
    void should_reject_blank_token() {
        assertThatThrownBy(() -> new ResetPasswordCommand("", "NewPass123!@#", "ua"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}