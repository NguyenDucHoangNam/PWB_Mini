package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.LogoutRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.LogoutCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.testsupport.AuthControllerHarness;
import com.pwb.shared.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static com.pwb.iam.testsupport.AuthControllerHarness.COOKIE_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the controller alone: request binding, what a use-case failure turns into on the wire, and
 * the refresh-token cookie. Whether an anonymous caller can reach {@code /logout} at all is
 * {@code SecurityConfig}'s answer, not this class's — the e2e flows check that against the real
 * filter chain. Authenticated cases here seed the security context, which is what
 * {@code @CurrentUser} reads.
 */
@DisplayName("AuthController")
class AuthControllerTest {

    private static final String VALID_PASSWORD = "StrongPass1!@#$";

    private AuthControllerHarness harness;

    @BeforeEach
    void setUp() {
        harness = new AuthControllerHarness();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("returns 201 with the new user id")
        void should_return_201() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new RegisterRequest("user@example.com", VALID_PASSWORD, "Alice"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(harness.userId.toString()))
                    .andExpect(jsonPath("$.data.message").isNotEmpty());
        }

        @Test
        @DisplayName("returns 400 when the email is blank")
        void should_return_400_on_blank_email() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new RegisterRequest("", VALID_PASSWORD, "Alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when the password is shorter than the policy allows")
        void should_return_400_on_short_password() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new RegisterRequest("user@example.com", "short", "Alice"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 409 when the email is already taken")
        void should_return_409_on_existing_email() throws Exception {
            when(harness.registerUseCase.execute(any(RegisterCommand.class))).thenThrow(
                    new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED,
                            Map.of("email", "user@example.com")));

            harness.mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new RegisterRequest("user@example.com", VALID_PASSWORD, "Alice"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IAM_001"));
        }

        @Test
        @DisplayName("passes the Accept-Language header down as the locale")
        void should_forward_locale() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new RegisterRequest("user@example.com", VALID_PASSWORD, "Alice")))
                            .header("Accept-Language", "en"))
                    .andExpect(status().isCreated());

            verify(harness.registerUseCase).execute(argThat(
                    (RegisterCommand command) -> "en".equals(command.locale())));
        }
    }

    @Nested
    @DisplayName("otp")
    class Otp {

        @Test
        @DisplayName("verify returns 200 with an access token and sets the refresh cookie")
        void should_return_200_on_verify() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new VerifyOtpRequest(harness.userId, "123456"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-stub"))
                    .andExpect(cookie().value(COOKIE_NAME, "refresh-token-stub"))
                    .andExpect(cookie().httpOnly(COOKIE_NAME, true));
        }

        @Test
        @DisplayName("verify returns 400 when the code is blank")
        void should_return_400_on_blank_code() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new VerifyOtpRequest(harness.userId, ""))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("verify returns 404 when the user is gone")
        void should_return_404_on_unknown_user() throws Exception {
            when(harness.verifyOtpUseCase.execute(any(VerifyOtpCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.USER_NOT_FOUND));

            harness.mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new VerifyOtpRequest(harness.userId, "123456"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IAM_003"));
        }

        @Test
        @DisplayName("resend returns 200")
        void should_return_200_on_resend() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/resend-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ResendOtpRequest(harness.userId, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(harness.userId.toString()));
        }

        @Test
        @DisplayName("resend returns 429 once the caller is throttled")
        void should_return_429_when_throttled() throws Exception {
            doThrow(new BusinessException(IamErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", 60)))
                    .when(harness.resendOtpUseCase).execute(any(ResendOtpCommand.class));

            harness.mockMvc.perform(post("/api/v1/auth/resend-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ResendOtpRequest(harness.userId, null))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("IAM_014"))
                    .andExpect(header().string("Retry-After", "60"))
                    .andExpect(jsonPath("$.error.retryAfterSeconds").value(60));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns 200 and keeps the refresh token out of the body")
        void should_return_200() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LoginRequest("active@example.com", VALID_PASSWORD)))
                            .header("User-Agent", "TestAgent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-stub"))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(cookie().value(COOKIE_NAME, "refresh-token-stub"));
        }

        @Test
        @DisplayName("returns 401 on bad credentials")
        void should_return_401_on_bad_credentials() throws Exception {
            when(harness.loginUseCase.execute(any(LoginCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS));

            harness.mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LoginRequest("active@example.com", "WrongPass1!@#$"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("IAM_004"));
        }

        @Test
        @DisplayName("returns 403 when the account is not active")
        void should_return_403_on_inactive_account() throws Exception {
            when(harness.loginUseCase.execute(any(LoginCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.ACCOUNT_INACTIVE));

            harness.mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LoginRequest("active@example.com", VALID_PASSWORD))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("IAM_006"));
        }

        @Test
        @DisplayName("returns 429 when the account is locked out")
        void should_return_429_when_locked() throws Exception {
            when(harness.loginUseCase.execute(any(LoginCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.ACCOUNT_LOCKED));

            harness.mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LoginRequest("active@example.com", VALID_PASSWORD))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("IAM_005"));
        }

        @Test
        @DisplayName("google login returns 200")
        void should_return_200_on_google_login() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/google-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new GoogleLoginRequest("valid-google-id-token-string")))
                            .header("Accept-Language", "vi"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-stub"));
        }

        @Test
        @DisplayName("google login returns 401 when the id token does not verify")
        void should_return_401_on_bad_google_token() throws Exception {
            when(harness.googleLoginUseCase.execute(any(GoogleLoginCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID));

            harness.mockMvc.perform(post("/api/v1/auth/google-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new GoogleLoginRequest("invalid-token-value"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("google login falls back to vi for a locale the app does not speak")
        void should_normalize_unsupported_locale() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/google-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new GoogleLoginRequest("valid-google-id-token-string")))
                            .header("Accept-Language", "fr-FR"))
                    .andExpect(status().isOk());

            verify(harness.googleLoginUseCase).execute(argThat(
                    (GoogleLoginCommand command) -> "vi".equals(command.locale())));
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("reads the refresh token from the cookie, not the body")
        void should_return_200_with_cookie() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(COOKIE_NAME, "refresh.jwt")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-stub"));

            verify(harness.refreshTokenUseCase).execute(argThat(
                    (RefreshTokenCommand command) -> "refresh.jwt".equals(command.rawRefreshToken())));
        }

        @Test
        @DisplayName("returns 401 when no cookie was sent")
        void should_return_401_without_cookie() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("IAM_011"));
        }

        @Test
        @DisplayName("returns 401 when the stored token is no longer valid")
        void should_return_401_on_invalid_token() throws Exception {
            when(harness.refreshTokenUseCase.execute(any(RefreshTokenCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.REFRESH_TOKEN_INVALID));

            harness.mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(COOKIE_NAME, "stale.jwt")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("returns 200 and expires the refresh cookie")
        void should_return_200_and_clear_cookie() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LogoutRequest("refresh.jwt", "jti", 900L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(harness.userId.toString()))
                    .andExpect(cookie().maxAge(COOKIE_NAME, 0));
        }

        @Test
        @DisplayName("prefers the cookie over the body when both carry a token")
        void should_prefer_cookie_over_body() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LogoutRequest("from-body", null, null)))
                            .cookie(new Cookie(COOKIE_NAME, "from-cookie")))
                    .andExpect(status().isOk());

            verify(harness.logoutUseCase).execute(argThat(
                    (LogoutCommand command) -> "from-cookie".equals(command.rawRefreshToken())));
        }

        @Test
        @DisplayName("falls back to the body when the client has no cookie")
        void should_fall_back_to_body() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new LogoutRequest("from-body", null, null))))
                    .andExpect(status().isOk());

            verify(harness.logoutUseCase).execute(argThat(
                    (LogoutCommand command) -> "from-body".equals(command.rawRefreshToken())));
        }

        @Test
        @DisplayName("accepts an empty body from a cookie-only client")
        void should_accept_empty_body() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie(COOKIE_NAME, "from-cookie")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("passwords")
    class Passwords {

        @Test
        @DisplayName("forgot-password answers without echoing a user id back")
        void should_not_echo_user_id() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ForgotPasswordRequest("active@example.com")))
                            .header("User-Agent", "TestAgent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").doesNotExist())
                    .andExpect(jsonPath("$.data.message").isNotEmpty());
        }

        @Test
        @DisplayName("forgot-password answers the same way for an address nobody registered")
        void should_answer_the_same_for_unknown_address() throws Exception {
            when(harness.forgotPasswordUseCase.execute(any()))
                    .thenReturn(ForgotPasswordUseCase.Result.sent(null, 60));

            harness.mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ForgotPasswordRequest("nobody@example.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").doesNotExist());
        }

        @Test
        @DisplayName("forgot-password returns 429 while the cooldown is running")
        void should_return_429_during_cooldown() throws Exception {
            when(harness.forgotPasswordUseCase.execute(any()))
                    .thenThrow(new BusinessException(IamErrorCode.PASSWORD_RESET_COOLDOWN));

            harness.mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ForgotPasswordRequest("active@example.com"))))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("reset returns 200 with the user whose password changed")
        void should_return_200_on_reset() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ResetPasswordRequest("valid-token", "NewPass1!@#$"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(harness.userId.toString()));
        }

        @Test
        @DisplayName("reset returns 400 when the token does not check out")
        void should_return_400_on_invalid_token() throws Exception {
            when(harness.resetPasswordUseCase.execute(any(ResetPasswordCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID));

            harness.mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ResetPasswordRequest("invalid-token", "NewPass1!@#$"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("reset returns 400 when the new password is too short")
        void should_return_400_on_short_new_password() throws Exception {
            harness.mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ResetPasswordRequest("token", "short"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("change returns 200 for the caller in the security context")
        void should_return_200_on_change() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ChangePasswordRequest("OldPass1!@#$", "NewPass1!@#$"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(harness.userId.toString()));

            verify(harness.changePasswordUseCase).execute(argThat(
                    (ChangePasswordCommand command) -> harness.userId.equals(command.userId())));
        }

        @Test
        @DisplayName("change returns 400 when the new password is too short")
        void should_return_400_on_short_new() throws Exception {
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ChangePasswordRequest("OldPass1!@#$", "short"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("change returns 400 when the current password is wrong")
        void should_return_400_on_wrong_current() throws Exception {
            when(harness.changePasswordUseCase.execute(any(ChangePasswordCommand.class)))
                    .thenThrow(new BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD));
            harness.authenticate();

            harness.mockMvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(harness.json(new ChangePasswordRequest("WrongOldPass1", "NewPass1!@#$"))))
                    .andExpect(status().isBadRequest());
        }
    }
}
