package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.PasswordResetPolicy;
import com.pwb.iam.domain.model.PasswordResetToken;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.PasswordResetTokenRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.PasswordResetTokenService;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubPasswordResetTokenService;
import com.pwb.iam.testsupport.TestUserBuilder;
import com.pwb.infra.mail.api.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private EmailDeliveryPort emailDeliveryPort;

    private StubPasswordResetTokenService passwordResetTokenService;
    private PasswordResetPolicy passwordResetPolicy;
    private ForgotPasswordUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        passwordResetTokenService = new StubPasswordResetTokenService();
        passwordResetPolicy = new PasswordResetPolicy("super-secret-token-key-1234567890", 30L, 60L, "http://localhost:3000", "/reset-password");
        when(throttlingService.enforceCooldownForPasswordReset(anyString())).thenReturn(0L);

        useCase = new ForgotPasswordUseCaseImpl(
                userRepository, passwordResetTokenRepository, passwordResetTokenService,
                throttlingService, authEventPublisher, passwordResetPolicy, emailDeliveryPort);
    }

    @Test
    @DisplayName("should send reset email when active local user exists")
    void should_send_reset_email() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        var result = useCase.execute(new ForgotPasswordCommand("active@example.com", "ua", "vi", "10.0.0.1"));

        assertThat(result.userId()).isEqualTo(user.getUserId());
        assertThat(result.status()).isEqualTo("SENT");
        verify(passwordResetTokenRepository).invalidateAllForUser(eq(user.getUserId()), any());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailDeliveryPort).enqueue(any());
        verify(authEventPublisher).publishPasswordResetRequested(eq(user.getUserId()), any(), anyString(), anyLong(), eq("ua"));
    }

    @Test
    @DisplayName("should return silent result when email unknown")
    void should_be_silent_when_email_unknown() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        var result = useCase.execute(new ForgotPasswordCommand("ghost@example.com", "ua", "vi", "10.0.0.1"));

        assertThat(result.userId()).isNull();
        assertThat(result.status()).isEqualTo("SENT");
        verify(emailDeliveryPort, never()).enqueue(any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return silent result when user not active")
    void should_be_silent_when_user_inactive() {
        User banned = TestUserBuilder.banned();
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(banned));

        var result = useCase.execute(new ForgotPasswordCommand("banned@example.com", "ua", "vi", "10.0.0.1"));

        assertThat(result.userId()).isEqualTo(banned.getUserId());
        verify(emailDeliveryPort, never()).enqueue(any());
    }

    @Test
    @DisplayName("should throw AUTH_OAUTH_USER_NO_PASSWORD when account is OAuth")
    void should_throw_for_oauth_user() {
        User google = TestUserBuilder.googleActive();
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordCommand("google@example.com", "ua", "vi", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OAUTH_USER_NO_PASSWORD");
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when cooldown active")
    void should_throw_rate_limited() {
        when(throttlingService.enforceCooldownForPasswordReset("user@example.com")).thenReturn(45L);

        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordCommand("user@example.com", "ua", "vi", "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should include resetLink and ttlMinutes in email variables")
    void should_include_reset_link_in_email() {
        User user = TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));

        useCase.execute(new ForgotPasswordCommand("active@example.com", "ua", "vi", "10.0.0.1"));

        ArgumentCaptor<com.pwb.iam.domain.service.EmailEnqueueCommand> captor =
                ArgumentCaptor.forClass(com.pwb.iam.domain.service.EmailEnqueueCommand.class);
        verify(emailDeliveryPort).enqueue(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(EmailTemplate.PASSWORD_RESET);
        assertThat(captor.getValue().variables()).containsKey("resetLink");
        assertThat(captor.getValue().variables()).containsEntry("ttlMinutes", "30");
        assertThat(captor.getValue().locale()).isEqualTo("vi");
    }

    @Test
    @DisplayName("should default locale and clientIp in command")
    void should_handle_defaults() {
        ForgotPasswordCommand cmd = new ForgotPasswordCommand("user@example.com", "ua");

        assertThat(cmd.locale()).isEqualTo("vi");
        assertThat(cmd.clientIp()).isEqualTo("unknown");
    }
}