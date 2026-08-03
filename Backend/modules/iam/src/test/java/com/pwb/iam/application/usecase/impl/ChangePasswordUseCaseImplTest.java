package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.iam.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubPasswordHasher;
import com.pwb.iam.testsupport.StubTokenManagerService;
import com.pwb.iam.testsupport.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ChangePasswordUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private PasswordHistoryJpaRepository passwordHistoryJpaRepository;
    @Mock private ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private ThrottlingService throttlingService;

    private StubPasswordHasher passwordHasher;
    private TokenManagerService tokenManagerService;
    private ChangePasswordUseCaseImpl useCase;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        passwordHasher = new StubPasswordHasher();
        tokenManagerService = new StubTokenManagerService(userId);
        lenient().when(throttlingService.consume(anyString(), any(int.class), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));

        useCase = new ChangePasswordUseCaseImpl(
                userRepository, passwordHistoryRepository, passwordHistoryJpaRepository,
                passwordHasher, validatePasswordPolicyUseCase, tokenManagerService,
                authEventPublisher, throttlingService);
    }

    @Test
    @DisplayName("should change password and revoke all refresh tokens")
    void should_change_password() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);

        var result = useCase.execute(new ChangePasswordCommand(userId, "Pass1234!@#", "NewPass123!@#", "ua", "10.0.0.1"));

        assertThat(result.userId()).isEqualTo(userId);
        verify(tokenManagerService).revokeAllRefreshTokensForUser(userId);
        verify(authEventPublisher).publishPasswordChanged(eq(userId), eq(user.getEmail().value()), eq("10.0.0.1"), eq("ua"));
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user missing")
    void should_throw_when_user_not_found() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "x", "y", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("should throw AUTH_OAUTH_USER_NO_PASSWORD for OAuth account")
    void should_throw_for_oauth_user() {
        User google = TestUserBuilder.googleActive();
        when(userRepository.findById(userId)).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "x", "y", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OAUTH_USER_NO_PASSWORD");
    }

    @Test
    @DisplayName("should throw AUTH_INVALID_CURRENT_PASSWORD when current password mismatches")
    void should_throw_when_current_password_wrong() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CorrectP@ssw0rd"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "WrongP@ssword", "NewPass123!@#", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_INVALID_CURRENT_PASSWORD");
    }

    @Test
    @DisplayName("should throw AUTH_PASSWORD_RECENTLY_USED when new password in history")
    void should_throw_when_recently_used() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CurrentPass123!@#"));
        PasswordHistory recent = PasswordHistory.create(userId, passwordHasher.hash("NewPass123!@#"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt()))
                .thenReturn(List.of(recent));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "CurrentPass123!@#", "NewPass123!@#", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_PASSWORD_RECENTLY_USED");
    }

    @Test
    @DisplayName("should throw AUTH_PASSWORD_REUSED when new equals current")
    void should_throw_when_reused() {
        String shared = "SamePass123!@#";
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash(shared));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, shared, shared, "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_PASSWORD_REUSED");
    }

    @Test
    @DisplayName("should propagate WEAK_PASSWORD from validator")
    void should_propagate_weak_password() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CurrentPass123!@#"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new BusinessException(IamErrorCode.WEAK_PASSWORD))
                .when(validatePasswordPolicyUseCase).validate("weak");

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "CurrentPass123!@#", "weak", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when throttled")
    void should_throw_rate_limited() {
        lenient().when(throttlingService.consume(anyString(), any(int.class), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));
        when(throttlingService.consume(org.mockito.ArgumentMatchers.contains("change-password"), any(int.class), any()))
                .thenReturn(ThrottlingService.ThrottleDecision.deny(45L));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordCommand(userId, "x", "y", "ua", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should delete oldest history entries when exceeding MAX_HISTORY_SIZE")
    void should_delete_oldest_when_exceeds() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CurrentPass123!@#"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(userId))
                .thenReturn((long) (PasswordHistory.MAX_HISTORY_SIZE + 3));

        useCase.execute(new ChangePasswordCommand(userId, "CurrentPass123!@#", "NewPass123!@#", "ua", "10.0.0.1"));

        verify(passwordHistoryRepository).deleteOldestByUserId(eq(userId), eq(3));
    }

    @Test
    @DisplayName("should default null clientIp to 'unknown'")
    void should_default_null_client_ip() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CurrentPass123!@#"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);

        var cmd = new ChangePasswordCommand(userId, "Pass1234!@#", "NewPass123!@#", "ua", null);
        var result = useCase.execute(cmd);

        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("should reject blank current/new password at command boundary")
    void should_reject_invalid_command() {
        assertThatThrownBy(() -> new ChangePasswordCommand(userId, "", "new", "ua", "ip"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangePasswordCommand(userId, "old", "", "ua", "ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null userId at command boundary")
    void should_reject_null_user_id() {
        assertThatThrownBy(() -> new ChangePasswordCommand(null, "old", "new", "ua", "ip"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should record previous password hash to history")
    void should_record_previous_password() {
        User user = TestUserBuilder.withPasswordHash(passwordHasher.hash("CurrentPass123!@#"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), anyInt())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordHistoryJpaRepository.countByUserIdAndDeletedFalse(userId)).thenReturn(0L);

        useCase.execute(new ChangePasswordCommand(userId, "CurrentPass123!@#", "NewPass123!@#", "ua", "10.0.0.1"));

        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
    }
}