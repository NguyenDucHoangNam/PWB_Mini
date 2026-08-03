package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.GoogleUserInfo;
import com.pwb.iam.domain.model.LoginPolicy;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import com.pwb.iam.domain.service.ThrottlingService;
import com.pwb.iam.domain.service.TokenManagerService;
import com.pwb.shared.exception.BusinessException;
import com.pwb.iam.testsupport.StubGoogleTokenVerifier;
import com.pwb.iam.testsupport.StubTokenManagerService;
import com.pwb.iam.testsupport.TestUserBuilder;
import com.pwb.iam.domain.model.Role;
import com.pwb.infra.mail.api.EmailTemplate;
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
class GoogleLoginUseCaseImplTest {

    private static final GoogleUserInfo PAYLOAD =
            new GoogleUserInfo("google-sub-1", "google@example.com", true, "Google User", "https://example.com/avatar.png");

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ThrottlingService throttlingService;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private EmailDeliveryPort emailDeliveryPort;

    private StubGoogleTokenVerifier googleTokenVerifier;
    private TokenManagerService tokenManagerService;
    private LoginPolicy loginPolicy;
    private GoogleLoginUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        googleTokenVerifier = new StubGoogleTokenVerifier();
        tokenManagerService = new StubTokenManagerService();
        loginPolicy = new LoginPolicy(10, 30, 10, 5, 15);
        lenient().when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));

        useCase = new GoogleLoginUseCaseImpl(
                userRepository, roleRepository, googleTokenVerifier, tokenManagerService,
                authEventPublisher, throttlingService, loginPolicy, emailDeliveryPort);
    }

    @Test
    @DisplayName("should register new google user and send welcome email")
    void should_register_new_google_user() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.USER))
                .thenReturn(Optional.of(Role.create(RoleName.USER, "Default")));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.rehydrate(UUID.randomUUID(), u.getEmail().value(), u.getPassword() == null ? null : u.getPassword().hash(),
                    u.getFullName(), u.getAvatarUrl(), null, u.getStatus(), u.getRole().name(),
                    u.getOauthProvider(), u.getOauthId());
        });

        var result = useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi"));

        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();
        verify(authEventPublisher).publishUserRegisteredGoogle(any(), eq("google@example.com"), any());
        verify(emailDeliveryPort).enqueue(any());
    }

    @Test
    @DisplayName("should login existing google user via oauth lookup")
    void should_login_existing_google_user() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        User existing = TestUserBuilder.googleActive();
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(existing));

        var result = useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi"));

        assertThat(result).isNotNull();
        verify(authEventPublisher).publishGoogleLoginSuccess(eq(existing.getUserId()), any(), any(), any());
    }

    @Test
    @DisplayName("should link oauth to existing local user when email matches")
    void should_link_to_existing_local_user() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        User local = TestUserBuilder.localPending();
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(local));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi"));

        verify(authEventPublisher).publishUserLinkedGoogle(any(), any(), any());
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when existing user BANNED")
    void should_throw_when_existing_user_banned() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        User banned = TestUserBuilder.banned();
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("should throw ACCOUNT_INACTIVE when local linked user BANNED")
    void should_throw_when_local_linked_user_banned() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        User banned = TestUserBuilder.banned();
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    @DisplayName("should publish failure event when token verifier throws")
    void should_publish_google_failure_when_verifier_throws() {
        googleTokenVerifier.presetFailure(new BusinessException(IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID));

        assertThatThrownBy(() -> useCase.execute(new GoogleLoginCommand("bad-token", "10.0.0.1", "ua", "vi")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("AUTH_GOOGLE_TOKEN_INVALID");

        verify(authEventPublisher).publishGoogleLoginFailed(eq("unknown"), any(), any(), any());
    }

    @Test
    @DisplayName("should throw RATE_LIMITED when throttled by IP")
    void should_throw_rate_limited_by_ip() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        lenient().when(throttlingService.consume(anyString(), anyInt(), any())).thenReturn(ThrottlingService.ThrottleDecision.allow(5L));
        when(throttlingService.consume(org.mockito.ArgumentMatchers.contains("google-login:ip:"), anyInt(), any()))
                .thenReturn(ThrottlingService.ThrottleDecision.deny(60L));

        assertThatThrownBy(() -> useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("should throw ROLE_NOT_FOUND when default role missing for new user")
    void should_throw_when_role_missing() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((IamErrorCode) ((BusinessException) ex).getErrorCode()).name())
                .isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("should include welcome email with displayName variable")
    void should_enqueue_welcome_email() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.USER))
                .thenReturn(Optional.of(Role.create(RoleName.USER, "Default")));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new GoogleLoginCommand("id-token", "10.0.0.1", "ua", "vi"));

        ArgumentCaptor<com.pwb.iam.domain.service.EmailEnqueueCommand> captor =
                ArgumentCaptor.forClass(com.pwb.iam.domain.service.EmailEnqueueCommand.class);
        verify(emailDeliveryPort).enqueue(captor.capture());
        assertThat(captor.getValue().template()).isEqualTo(EmailTemplate.WELCOME_GOOGLE);
        assertThat(captor.getValue().variables()).containsEntry("displayName", "Google User");
        assertThat(captor.getValue().locale()).isEqualTo("vi");
    }

    @Test
    @DisplayName("should default null clientIp to 'unknown'")
    void should_handle_null_client_ip() {
        googleTokenVerifier.presetPayload(PAYLOAD);
        User existing = TestUserBuilder.googleActive();
        when(userRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(existing));

        var result = useCase.execute(new GoogleLoginCommand("id-token", null, "ua", "vi"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should reject blank idToken at command boundary")
    void should_reject_blank_id_token() {
        assertThatThrownBy(() -> new GoogleLoginCommand("", "ip", "ua"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}