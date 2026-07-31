package com.pwb.iam.application.facade;

import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.model.AuthNextStep;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.testsupport.TestUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamFacadeImplTest {

    @Mock private RegisterUseCase registerUseCase;
    @Mock private VerifyOtpUseCase verifyOtpUseCase;
    @Mock private ResendOtpUseCase resendOtpUseCase;
    @Mock private LoginUseCase loginUseCase;
    @Mock private RefreshTokenUseCase refreshTokenUseCase;
    @Mock private LogoutUseCase logoutUseCase;
    @Mock private ForgotPasswordUseCase forgotPasswordUseCase;
    @Mock private ResetPasswordUseCase resetPasswordUseCase;
    @Mock private ChangePasswordUseCase changePasswordUseCase;
    @Mock private GoogleLoginUseCase googleLoginUseCase;

    private IamFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new IamFacadeImpl(
                registerUseCase, verifyOtpUseCase, resendOtpUseCase, loginUseCase,
                refreshTokenUseCase, logoutUseCase, forgotPasswordUseCase,
                resetPasswordUseCase, changePasswordUseCase, googleLoginUseCase);
    }

    @Test
    @DisplayName("register should delegate and return userId")
    void should_delegate_register() {
        User user = TestUserBuilder.localActive();
        when(registerUseCase.execute(any(RegisterCommand.class))).thenReturn(user);

        UUID userId = facade.register(new RegisterCommand("user@example.com", "StrongP@ss123!", "Alice"));

        assertThat(userId).isEqualTo(user.getUserId());
        verify(registerUseCase).execute(any(RegisterCommand.class));
    }

    @Test
    @DisplayName("verifyOtp should delegate and convert LoginResult to AuthView")
    void should_delegate_verify_otp() {
        User user = TestUserBuilder.localActive();
        LoginResult result = new LoginResult(
                user,
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).accessToken(),
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).refreshToken(),
                AuthNextStep.NONE);
        when(verifyOtpUseCase.execute(any(VerifyOtpCommand.class))).thenReturn(result);

        AuthView view = facade.verifyOtp(new VerifyOtpCommand(user.getUserId(), "123456"));

        assertThat(view.userId()).isEqualTo(user.getUserId());
        assertThat(view.email()).isEqualTo(user.getEmail().value());
        assertThat(view.status()).isEqualTo(user.getStatus().name());
        assertThat(view.role()).isEqualTo(user.getRole().name());
    }

    @Test
    @DisplayName("resendOtp should delegate")
    void should_delegate_resend_otp() {
        facade.resendOtp(new ResendOtpCommand(UUID.randomUUID(), com.pwb.iam.domain.model.OtpPurpose.REGISTER));

        verify(resendOtpUseCase).execute(any(ResendOtpCommand.class));
    }

    @Test
    @DisplayName("login should delegate and convert to AuthView")
    void should_delegate_login() {
        User user = TestUserBuilder.localActive();
        LoginResult result = new LoginResult(
                user,
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).accessToken(),
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).refreshToken());
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(result);

        AuthView view = facade.login(new LoginCommand("active@example.com", "Pass1234!@#", "ip", "ua"));

        assertThat(view.email()).isEqualTo("active@example.com");
    }

    @Test
    @DisplayName("refresh should delegate")
    void should_delegate_refresh() {
        User user = TestUserBuilder.localActive();
        LoginResult result = new LoginResult(
                user,
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).accessToken(),
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).refreshToken());
        when(refreshTokenUseCase.execute(any(RefreshTokenCommand.class))).thenReturn(result);

        AuthView view = facade.refresh(new RefreshTokenCommand("raw", "ip"));

        assertThat(view.userId()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("logout should delegate and return command.userId")
    void should_delegate_logout() {
        UUID userId = UUID.randomUUID();
        UUID result = facade.logout(new LogoutCommand(userId, "raw", "jti", 900L, "ip", "ua"));

        assertThat(result).isEqualTo(userId);
        verify(logoutUseCase).execute(any(LogoutCommand.class));
    }

    @Test
    @DisplayName("loginWithGoogle should delegate")
    void should_delegate_login_with_google() {
        User user = TestUserBuilder.googleActive();
        LoginResult result = new LoginResult(
                user,
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).accessToken(),
                new com.pwb.iam.testsupport.StubTokenManagerService(user.getUserId()).refreshToken());
        when(googleLoginUseCase.execute(any(GoogleLoginCommand.class))).thenReturn(result);

        AuthView view = facade.loginWithGoogle(new GoogleLoginCommand("id-token", "ip", "ua", "vi"));

        assertThat(view.email()).isEqualTo("google@example.com");
    }

    @Test
    @DisplayName("forgotPassword should delegate")
    void should_delegate_forgot_password() {
        ForgotPasswordUseCase.Result result = ForgotPasswordUseCase.Result.sent(UUID.randomUUID(), 60);
        when(forgotPasswordUseCase.execute(any(ForgotPasswordCommand.class))).thenReturn(result);

        ForgotPasswordUseCase.Result actual = facade.forgotPassword(new ForgotPasswordCommand("user@example.com", "ua"));

        assertThat(actual).isSameAs(result);
    }

    @Test
    @DisplayName("resetPassword should delegate and return userId")
    void should_delegate_reset_password() {
        UUID userId = UUID.randomUUID();
        when(resetPasswordUseCase.execute(any(ResetPasswordCommand.class)))
                .thenReturn(new ResetPasswordUseCase.Result(userId));

        UUID result = facade.resetPassword(new ResetPasswordCommand("token", "NewPass123!@#", "ua"));

        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("changePassword should delegate and return userId")
    void should_delegate_change_password() {
        UUID userId = UUID.randomUUID();
        when(changePasswordUseCase.execute(any(ChangePasswordCommand.class)))
                .thenReturn(new ChangePasswordUseCase.Result(userId));

        UUID result = facade.changePassword(new ChangePasswordCommand(userId, "OldP@ss", "NewP@ss", "ua", "ip"));

        assertThat(result).isEqualTo(userId);
    }
}