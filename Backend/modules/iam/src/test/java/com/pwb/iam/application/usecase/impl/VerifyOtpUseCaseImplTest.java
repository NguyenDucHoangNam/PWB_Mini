package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.service.OtpAttemptRecorder;
import com.pwb.iam.application.service.RateLimitGuard;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.exception.OtpVerificationException;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubOtpGenerator;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyOtpUseCaseImplTest {

    private static final String CODE = "123456";
    private static final String HASHED_CODE = "hashed:" + CODE;

    @Mock private UserRepository userRepository;
    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;

    private StubOtpGenerator otpGenerator;
    private TokenManagerService tokenManagerService;
    private VerifyOtpUseCaseImpl useCase;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otpGenerator = new StubOtpGenerator().presetNextCode(CODE).presetHash(CODE, HASHED_CODE);
        tokenManagerService = new StubTokenManagerService(userId);
        lenient().when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(10L));

        OtpPolicy otpPolicy = new OtpPolicy(10, 60, 5, 6, 10);
        LoginPolicy loginPolicy = new LoginPolicy(10, 30, 10, 5, 10, 5, 5, 15);

        // The attempt counter is written through OtpAttemptRecorder in its own transaction, so that
        // a wrong code still costs an attempt after the failure rolls the main one back. A real
        // recorder over the mocked repository keeps that write visible to this test's verifications.
        useCase = new VerifyOtpUseCaseImpl(
                userRepository, otpCodeRepository, otpGenerator, tokenManagerService, authEventPublisher,
                new RateLimitGuard(throttlingService), new OtpAttemptRecorder(otpCodeRepository, otpPolicy),
                otpPolicy, loginPolicy);
    }

    @Test
    @DisplayName("should mark user ACTIVE and issue both access/refresh tokens")
    void should_verify_and_issue_tokens() {
        User user = TestUserBuilder.withUserId(userId);
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, HASHED_CODE, Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).thenReturn(Optional.of(otp));
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new VerifyOtpCommand(userId, CODE, "10.0.0.1"));

        assertThat(result.user().getStatus()).isEqualTo(com.pwb.iam.domain.model.UserStatus.ACTIVE);
        assertThat(result.accessToken().tokenValue()).isEqualTo("access-token-stub");
        assertThat(result.refreshToken().rawToken()).isEqualTo("refresh-token-stub");

        verify(authEventPublisher).publishAuthSuccess(any(AuthSuccessEvent.class));
    }

    @Test
    @DisplayName("should throw USER_NOT_FOUND when user missing")
    void should_throw_when_user_not_found() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new VerifyOtpCommand(userId, CODE, "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("USER_NOT_FOUND");

        verify(otpCodeRepository, never()).findActiveByUserAndPurpose(any(), any());
    }

    @Test
    @DisplayName("should throw AUTH_OTP_EXPIRED when no active OTP found")
    void should_throw_when_otp_missing() {
        User user = TestUserBuilder.withUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new VerifyOtpCommand(userId, CODE, "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OTP_EXPIRED");
    }

    @Test
    @DisplayName("should propagate OtpVerificationException for wrong code")
    void should_throw_when_otp_wrong() {
        User user = TestUserBuilder.withUserId(userId);
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, HASHED_CODE, Instant.now().plus(5, ChronoUnit.MINUTES));

        // No save stub: a wrong code throws out of verify() before the use case saves, precisely so
        // the rollback cannot swallow the attempt counter. The failed attempt is written separately
        // through OtpAttemptRecorder instead.
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> useCase.execute(new VerifyOtpCommand(userId, "WRONG", "10.0.0.1")))
                .isInstanceOf(OtpVerificationException.class)
                .extracting(ex -> ((IamErrorCode) ((OtpVerificationException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_OTP_INVALID");
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when throttled by user id key")
    void should_throw_rate_limited_by_user() {
        lenient().when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(10L));
        // RateLimitGuard.checkIpAndSubject spends two buckets: "verify-otp:ip:<addr>" and
        // "verify-otp:subject:<userId>". Denying the second is what "throttled by user id" means —
        // matching on the literal "userId" matched neither key, so this test used to sail past the
        // limiter and fail later on a missing user.
        when(throttlingService.consume(org.mockito.ArgumentMatchers.contains("subject"), anyInt(), any()))
                .thenReturn(ThrottlingService.ThrottleDecision.deny(45L));

        assertThatThrownBy(() -> useCase.execute(new VerifyOtpCommand(userId, CODE, "10.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should publish OtpVerifiedDomainEvent after success")
    void should_publish_otp_verified_event() {
        User user = TestUserBuilder.withUserId(userId);
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, HASHED_CODE, Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).thenReturn(Optional.of(otp));
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new VerifyOtpCommand(userId, CODE, "10.0.0.1"));

        ArgumentCaptor<com.pwb.iam.domain.event.OtpVerifiedDomainEvent> captor =
                ArgumentCaptor.forClass(com.pwb.iam.domain.event.OtpVerifiedDomainEvent.class);
        verify(authEventPublisher).publishOtpVerified(captor.capture());
        assertThat(captor.getValue().purpose()).isEqualTo(OtpPurpose.REGISTER);
    }

    @Test
    @DisplayName("should set clientIp from command defaulting to 'unknown'")
    void should_handle_blank_client_ip() {
        VerifyOtpCommand cmd = new VerifyOtpCommand(userId, CODE, "");
        User user = TestUserBuilder.withUserId(userId);
        OtpCode otp = OtpCode.create(userId, OtpPurpose.REGISTER, HASHED_CODE, Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpCodeRepository.findActiveByUserAndPurpose(userId, OtpPurpose.REGISTER)).thenReturn(Optional.of(otp));
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(cmd);

        ArgumentCaptor<AuthSuccessEvent> captor = ArgumentCaptor.forClass(AuthSuccessEvent.class);
        verify(authEventPublisher).publishAuthSuccess(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should reject null userId and blank code at command boundary")
    void should_reject_invalid_command() {
        assertThatThrownBy(() -> new VerifyOtpCommand(null, CODE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerifyOtpCommand(userId, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}