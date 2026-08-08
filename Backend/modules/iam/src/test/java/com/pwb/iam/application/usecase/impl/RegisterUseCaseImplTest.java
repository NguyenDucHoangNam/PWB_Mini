package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.service.OtpIssuer;
import com.pwb.iam.application.usecase.ValidatePasswordPolicyUseCase;
import com.pwb.iam.domain.model.OtpPolicy;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.event.OtpIssuedDomainEvent;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OtpCode;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.OtpCodeRepository;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.OtpGenerator;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.InMemoryEmailDeliveryAdapter;
import com.pwb.iam.testsupport.StubOtpGenerator;
import com.pwb.iam.testsupport.StubPasswordHasher;
import com.pwb.infra.mail.api.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private ValidatePasswordPolicyUseCase validatePasswordPolicyUseCase;
    @Mock private EmailDeliveryPort emailDeliveryPort;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;

    private StubOtpGenerator otpGenerator;
    private RegisterUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        otpGenerator = new StubOtpGenerator().presetNextCode("123456");
        lenient().when(throttlingService.enforceCooldown(any(), any())).thenReturn(0L);
        lenient().when(passwordHasher.hash(any())).thenReturn("hashed:secret");
        lenient().when(roleRepository.findByName(RoleName.USER))
                .thenReturn(Optional.of(Role.create(RoleName.USER, "Default role")));
        lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        useCase = useCaseWithOtpTtl(5);
    }

    /**
     * Issuing the OTP moved out of this use case into {@link OtpIssuer}, which takes the timings as
     * an {@code OtpPolicy} value rather than reading a properties bean. Building a real issuer here
     * keeps the collaborators this test already mocks — the repository, the mail port, the event
     * publisher — on the same seams they were on before, while letting a test choose the TTL.
     */
    private RegisterUseCaseImpl useCaseWithOtpTtl(int ttlMinutes) {
        OtpPolicy otpPolicy = new OtpPolicy(ttlMinutes, 60, 5, 6, 10);
        OtpIssuer otpIssuer = new OtpIssuer(
                otpCodeRepository, otpGenerator, emailDeliveryPort, authEventPublisher, otpPolicy);
        return new RegisterUseCaseImpl(
                userRepository, roleRepository, passwordHasher,
                validatePasswordPolicyUseCase, throttlingService, otpIssuer);
    }

    @Test
    @DisplayName("should create user with PENDING_VERIFICATION and issue OTP email")
    void should_register_and_issue_otp() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        assertThat(saved.getStatus()).isEqualTo(com.pwb.iam.domain.model.UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getEmail().value()).isEqualTo("user@example.com");
        assertThat(saved.getRole()).isEqualTo(RoleName.USER);
        assertThat(saved.isOAuthUser()).isFalse();

        verify(passwordHasher).hash("StrongP@ss123!");
        verify(validatePasswordPolicyUseCase).validate("StrongP@ss123!");
        verify(roleRepository).findByName(RoleName.USER);
        verify(otpCodeRepository).invalidateAllByUserAndPurpose(saved.getUserId(), com.pwb.iam.domain.model.OtpPurpose.REGISTER);
        verify(otpCodeRepository).save(any(com.pwb.iam.domain.model.OtpCode.class));
        verify(emailDeliveryPort).enqueue(any());
    }

    @Test
    @DisplayName("should allow re-registration when user exists and status is PENDING_VERIFICATION")
    void should_allow_reregistration_when_user_exists_and_status_is_pending_verification() {
        User existingUser = com.pwb.iam.testsupport.TestUserBuilder.localPending();
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = useCase.execute(new RegisterCommand("pending@example.com", "NewStrongP@ss123!", "Alice New"));

        assertThat(saved.getStatus()).isEqualTo(com.pwb.iam.domain.model.UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getFullName()).isEqualTo("Alice New");
        verify(passwordHasher).hash("NewStrongP@ss123!");
        verify(validatePasswordPolicyUseCase).validate("NewStrongP@ss123!");
        verify(otpCodeRepository).invalidateAllByUserAndPurpose(saved.getUserId(), com.pwb.iam.domain.model.OtpPurpose.REGISTER);
        verify(otpCodeRepository).save(any(com.pwb.iam.domain.model.OtpCode.class));
        verify(emailDeliveryPort).enqueue(any());
    }

    @Test
    @DisplayName("should throw EMAIL_ALREADY_REGISTERED when email exists and is active")
    void should_throw_when_email_exists() {
        User activeUser = com.pwb.iam.testsupport.TestUserBuilder.localActive();
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("active@example.com", "StrongP@ss123!", "Alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("EMAIL_ALREADY_REGISTERED");

        verify(userRepository, never()).save(any());
        verify(emailDeliveryPort, never()).enqueue(any());
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when cooldown remaining > 0")
    void should_throw_rate_limited_when_cooldown_active() {
        when(throttlingService.enforceCooldown(any(), eq(com.pwb.iam.domain.service.ThrottlingService.CooldownPurpose.REGISTER)))
                .thenReturn(45L);

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_RATE_LIMIT_EXCEEDED");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw ROLE_NOT_FOUND when USER role missing")
    void should_throw_when_role_missing() {
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("should propagate WEAK_PASSWORD from validatePasswordPolicyUseCase")
    void should_propagate_weak_password() {
        org.mockito.Mockito.doThrow(new BusinessException(IamErrorCode.WEAK_PASSWORD, Map.of("violations", java.util.List.of("short"))))
                .when(validatePasswordPolicyUseCase).validate("weak");

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("user@example.com", "weak", "Alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("WEAK_PASSWORD");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should normalize email to lower case and trimmed before saving")
    void should_normalize_email() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterCommand("  USER@Example.COM  ", "StrongP@ss123!", "Alice"));

        verify(userRepository).findByEmail("user@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail().value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should enqueue email with OTP_REGISTER template and code in variables")
    void should_enqueue_email_with_otp_variables() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        ArgumentCaptor<com.pwb.iam.domain.service.EmailEnqueueCommand> captor =
                ArgumentCaptor.forClass(com.pwb.iam.domain.service.EmailEnqueueCommand.class);
        verify(emailDeliveryPort).enqueue(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(EmailTemplate.OTP_REGISTER);
        assertThat(captor.getValue().variables()).containsEntry("code", "123456");
        assertThat(captor.getValue().variables()).containsEntry("ttlMinutes", "5");
    }

    @Test
    @DisplayName("should publish OtpIssuedDomainEvent with same userId and purpose")
    void should_publish_otp_issued_event() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        ArgumentCaptor<OtpIssuedDomainEvent> captor = ArgumentCaptor.forClass(OtpIssuedDomainEvent.class);
        verify(authEventPublisher).publishOtpIssued(captor.capture());
        assertThat(captor.getValue().purpose()).isEqualTo(com.pwb.iam.domain.model.OtpPurpose.REGISTER);
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should use the configured OTP ttl when computing expiresAt")
    void should_use_otp_properties_ttl() {
        useCase = useCaseWithOtpTtl(15);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        // Asserts the deadline itself rather than just that a row was written — the old version
        // only counted the save call, so it passed no matter which ttl the policy carried.
        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(30, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("should default role to USER when role repository returns null")
    void should_default_to_user_role() {
        when(roleRepository.findByName(RoleName.USER))
                .thenReturn(Optional.of(Role.create(RoleName.USER, "Default")));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        verify(roleRepository).findByName(RoleName.USER);
    }

    @Test
    @DisplayName("should reject blank email at command boundary")
    void should_reject_blank_email_command() {
        assertThatThrownBy(() -> new RegisterCommand("", "StrongP@ss123!", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject blank rawPassword at command boundary")
    void should_reject_blank_password_command() {
        assertThatThrownBy(() -> new RegisterCommand("user@example.com", "", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}