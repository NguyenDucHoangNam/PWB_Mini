package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.LoginAttemptChecker;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubLoginAttemptChecker;
import com.pwb.iam.testsupport.StubPasswordHasher;
import com.pwb.iam.testsupport.StubTokenManagerService;
import com.pwb.iam.testsupport.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;

    private StubPasswordHasher passwordHasher;
    private StubLoginAttemptChecker attemptChecker;
    private TokenManagerService tokenManagerService;
    private LoginPolicy loginPolicy;
    private LoginUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        passwordHasher = new StubPasswordHasher();
        attemptChecker = new StubLoginAttemptChecker().notLocked();
        loginPolicy = new LoginPolicy(10, 30, 10, 5, 10, 5, 5, 15);
        UUID userId = UUID.randomUUID();
        tokenManagerService = new StubTokenManagerService(userId);

        lenient().when(throttlingService.consume(anyString(), any(int.class), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));

        // The use case takes a RateLimitGuard rather than the ThrottlingService directly. The guard
        // is a thin translation layer — it turns a denied decision into RATE_LIMITED — so a real one
        // over the mocked service keeps the stubbing above meaningful instead of mocking the guard.
        useCase = new LoginUseCaseImpl(
                userRepository, passwordHasher, new RateLimitGuard(throttlingService), attemptChecker,
                tokenManagerService, authEventPublisher, loginPolicy);
    }

    @Test
    @DisplayName("should return tokens and reset attempt counters for valid credentials")
    void should_login_active_user() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        var result = useCase.execute(new LoginCommand("active@example.com", "Pass1234!@#", "10.0.0.1", "ua"));

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();
        verify(authEventPublisher).publishAuthSuccess(any(AuthSuccessEvent.class));
    }

    @Test
    @DisplayName("should throw ACCOUNT_LOCKED when account locked and publish failure event")
    void should_throw_account_locked() {
        attemptChecker.locked(120L);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("user@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_LOCKED");

        verify(authEventPublisher).publishLoginFailed(eq("user@example.com"), eq("10.0.0.1"), eq("ua"), eq("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("should throw LOGIN_BAD_CREDENTIALS when user not found")
    void should_throw_when_user_not_found() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("missing@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("LOGIN_BAD_CREDENTIALS");

        assertThat(attemptChecker.failureCount()).isEqualTo(1);
        // BAD_CREDENTIALS, not USER_NOT_FOUND — the same reason the wrong-password case records.
        // The audit trail is readable by operators but is fed from a public endpoint, so recording
        // which of the two failed would rebuild the account-existence oracle that the identical
        // client-facing error was written to close.
        verify(authEventPublisher).publishLoginFailed(eq("missing@example.com"), any(), any(), eq("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("should throw LOGIN_BAD_CREDENTIALS when password mismatches")
    void should_throw_when_password_wrong() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("active@example.com", "WRONG", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("LOGIN_BAD_CREDENTIALS");

        assertThat(attemptChecker.failureCount()).isEqualTo(1);
        verify(authEventPublisher).publishLoginFailed(any(), any(), any(), eq("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when user BANNED")
    void should_throw_when_banned() {
        User user = TestUserBuilder.banned();
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("banned@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");

        verify(authEventPublisher).publishLoginFailed(any(), any(), any(), eq("ACCOUNT_INACTIVE"));
    }

    @Test
    @DisplayName("should throw ACCOUNT_NOT_VERIFIED when user PENDING_VERIFICATION")
    void should_throw_when_pending_verification() {
        User user = TestUserBuilder.localPending();
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("pending@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_NOT_VERIFIED");

        verify(authEventPublisher).publishLoginFailed(any(), any(), any(), eq("ACCOUNT_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when user DELETED")
    void should_throw_when_deleted() {
        User user = TestUserBuilder.deleted();
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("deleted@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when IP rate-limited")
    void should_throw_rate_limited_by_ip() {
        lenient().when(throttlingService.consume(anyString(), any(int.class), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));
        when(throttlingService.consume(org.mockito.ArgumentMatchers.contains("login:ip:"), any(int.class), any()))
                .thenReturn(ThrottlingService.ThrottleDecision.deny(60L));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("user@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should reset both email and IP attempt counters on success")
    void should_reset_counters_on_success() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        useCase.execute(new LoginCommand("active@example.com", "Pass1234!@#", "10.0.0.1", "ua"));

        assertThat(attemptChecker.resetCount()).isEqualTo(1);
        assertThat(attemptChecker.resetIpLockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should default null clientIp to 'unknown'")
    void should_handle_null_client_ip() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        var result = useCase.execute(new LoginCommand("active@example.com", "Pass1234!@#", null, "ua"));

        assertThat(result).isNotNull();
        // LoginCommand's compact constructor substitutes "unknown", so the null never reaches the
        // lockout counter or the audit trail — a caller with no resolvable address gets one shared
        // bucket rather than a null key.
        ArgumentCaptor<AuthSuccessEvent> captor = ArgumentCaptor.forClass(AuthSuccessEvent.class);
        verify(authEventPublisher).publishAuthSuccess(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("unknown");
        assertThat(captor.getValue().userAgent()).isEqualTo("ua");
    }

    @Test
    @DisplayName("should throw LOGIN_BAD_CREDENTIALS when user has no password (oauth account)")
    void should_throw_when_oauth_account_no_password() {
        User user = TestUserBuilder.googleActive();
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("google@example.com", "Pass1234!@#", "10.0.0.1", "ua")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("LOGIN_BAD_CREDENTIALS");
    }

    @Test
    @DisplayName("should reject blank email/password at command boundary")
    void should_reject_invalid_command() {
        assertThatThrownBy(() -> new LoginCommand("", "Pass1234!@#", "ip", "ua"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginCommand("user@example.com", "", "ip", "ua"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should lowercase and trim email before lookup")
    void should_normalize_email() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        useCase.execute(new LoginCommand("  ACTIVE@Example.COM ", "Pass1234!@#", "ip", "ua"));

        verify(userRepository).findByEmail("active@example.com");
    }
}