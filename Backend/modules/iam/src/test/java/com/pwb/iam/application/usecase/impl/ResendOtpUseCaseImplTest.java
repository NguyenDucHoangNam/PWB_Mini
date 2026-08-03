package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubOtpGenerator;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendOtpUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private EmailDeliveryPort emailDeliveryPort;
    @Mock private OtpProperties otpProperties;

    private StubOtpGenerator otpGenerator;
    private ResendOtpUseCaseImpl useCase;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otpGenerator = new StubOtpGenerator().presetNextCode("654321");
        lenient().when(otpProperties.getTtlMinutes()).thenReturn(10);
        lenient().when(otpProperties.getDailyLimit()).thenReturn(10);
        lenient().when(throttlingService.enforceCooldown(anyString(), any())).thenReturn(0L);

        useCase = new ResendOtpUseCaseImpl(
                userRepository, otpCodeRepository, otpGenerator, emailDeliveryPort, throttlingService, authEventPublisher, otpProperties);
    }

    @Test
    @DisplayName("should resend OTP and enqueue email")
    void should_resend_otp() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.countIssuedSince(eq(userId), eq(OtpPurpose.REGISTER), any())).thenReturn(0);

        useCase.execute(new ResendOtpCommand(userId, OtpPurpose.REGISTER));

        verify(otpCodeRepository).invalidateAllByUserAndPurpose(userId, OtpPurpose.REGISTER);
        verify(otpCodeRepository).save(any(OtpCode.class));
        verify(emailDeliveryPort).enqueue(any());
        verify(authEventPublisher).publishOtpIssued(any());
    }

    @Test
    @DisplayName("should throw AUTH_RATE_LIMIT_EXCEEDED when cooldown active")
    void should_throw_when_cooldown_active() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(throttlingService.enforceCooldown(anyString(), eq(ThrottlingService.CooldownPurpose.RESEND_OTP)))
                .thenReturn(30L);

        assertThatThrownBy(() -> useCase.execute(new ResendOtpCommand(userId, OtpPurpose.REGISTER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_RATE_LIMIT_EXCEEDED");

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw AUTH_OTP_DAILY_LIMIT_EXCEEDED when daily limit reached")
    void should_throw_when_daily_limit_reached() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.countIssuedSince(eq(userId), eq(OtpPurpose.REGISTER), any())).thenReturn(10);

        assertThatThrownBy(() -> useCase.execute(new ResendOtpCommand(userId, OtpPurpose.REGISTER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OTP_DAILY_LIMIT_EXCEEDED");

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user missing")
    void should_throw_when_user_missing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ResendOtpCommand(userId, OtpPurpose.REGISTER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("should publish OtpIssuedDomainEvent with PASSWORD_RESET purpose")
    void should_resend_otp_for_password_reset() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.countIssuedSince(eq(userId), eq(OtpPurpose.PASSWORD_RESET), any())).thenReturn(0);

        useCase.execute(new ResendOtpCommand(userId, OtpPurpose.PASSWORD_RESET));

        ArgumentCaptor<com.pwb.iam.domain.event.OtpIssuedDomainEvent> captor =
                ArgumentCaptor.forClass(com.pwb.iam.domain.event.OtpIssuedDomainEvent.class);
        verify(authEventPublisher).publishOtpIssued(captor.capture());
        assertThat(captor.getValue().purpose()).isEqualTo(OtpPurpose.PASSWORD_RESET);
    }
}