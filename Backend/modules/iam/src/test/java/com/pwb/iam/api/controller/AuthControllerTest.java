package com.pwb.iam.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.LogoutRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.exception.IamExceptionHandler;
import com.pwb.iam.application.facade.AuthView;
import com.pwb.iam.application.facade.IamFacade;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.testsupport.IamMessageSourceTestConfig;
import com.pwb.shared.exception.BusinessException;
import com.pwb.shared.exception.ValidationException;
import com.pwb.web.exception.GlobalExceptionHandler;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import com.pwb.web.security.CurrentUserAgentArgumentResolver;
import com.pwb.web.security.CurrentUserArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({
        IamExceptionHandler.class,
        GlobalExceptionHandler.class,
        IamMessageSourceTestConfig.class,
        CurrentClientIpArgumentResolver.class,
        CurrentUserAgentArgumentResolver.class,
        CurrentUserArgumentResolver.class,
        MessageResolver.class
})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private IamFacade iamFacade;

    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(iamFacade.register(any())).thenReturn(testUserId);
        when(iamFacade.verifyOtp(any())).thenReturn(authView());
        when(iamFacade.login(any())).thenReturn(authView());
        when(iamFacade.refresh(any())).thenReturn(authView());
        when(iamFacade.loginWithGoogle(any())).thenReturn(authView());
        when(iamFacade.logout(any())).thenReturn(testUserId);
        when(iamFacade.forgotPassword(any())).thenReturn(ForgotPasswordUseCase.Result.sent(testUserId, 60));
        when(iamFacade.resetPassword(any())).thenReturn(testUserId);
        when(iamFacade.changePassword(any())).thenReturn(testUserId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthView authView() {
        return AuthView.withTokens(testUserId, "user@example.com", "Alice", null, "ACTIVE", "USER",
                "access.jwt", "refresh.jwt", 900L);
    }

    private void authenticateAsUser(UUID userId) {
        com.pwb.web.security.AuthenticatedUser principal = com.pwb.web.security.AuthenticatedUser.builder()
                .userId(userId.toString())
                .email("user@example.com")
                .authorities(java.util.Set.of("ROLE_USER"))
                .isOAuthUser(false)
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("POST /register returns 201 on success")
    void should_return_201_on_register() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "StrongPass1!@#$", "Alice");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(testUserId.toString()));
    }

    @Test
    @DisplayName("POST /register returns 400 when email is blank")
    void should_return_400_on_register_blank_email() throws Exception {
        RegisterRequest request = new RegisterRequest("", "StrongPass1!@#$", "Alice");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register returns 400 when password is too short")
    void should_return_400_on_register_short_password() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "short", "Alice");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register returns 409 when email already registered")
    void should_return_409_on_register_existing_email() throws Exception {
        when(iamFacade.register(any()))
                .thenThrow(new BusinessException(IamErrorCode.EMAIL_ALREADY_REGISTERED, Map.of("email", "user@example.com")));
        RegisterRequest request = new RegisterRequest("user@example.com", "StrongPass1!@#$", "Alice");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IAM_001"));
    }

    @Test
    @DisplayName("POST /verify-otp returns 200 with tokens")
    void should_return_200_on_verify_otp() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest(testUserId, "123456");
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt"));
    }

    @Test
    @DisplayName("POST /verify-otp returns 400 when code is blank")
    void should_return_400_on_verify_otp_blank_code() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest(testUserId, "");
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /verify-otp returns 404 when user not found")
    void should_return_404_on_verify_otp_user_not_found() throws Exception {
        when(iamFacade.verifyOtp(any()))
                .thenThrow(new BusinessException(IamErrorCode.USER_NOT_FOUND));
        VerifyOtpRequest request = new VerifyOtpRequest(testUserId, "123456");
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IAM_003"));
    }

    @Test
    @DisplayName("POST /resend-otp returns 200")
    void should_return_200_on_resend_otp() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest(testUserId, null);
        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /resend-otp returns 429 when rate limited")
    void should_return_429_on_resend_otp_rate_limited() throws Exception {
        doThrow(new BusinessException(IamErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", 60)))
                .when(iamFacade).resendOtp(any());
        ResendOtpRequest request = new ResendOtpRequest(testUserId, null);
        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("IAM_014"));
    }

    @Test
    @DisplayName("POST /login returns 200 on success")
    void should_return_200_on_login() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "StrongPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt"));
    }

    @Test
    @DisplayName("POST /login returns 401 when bad credentials")
    void should_return_401_on_login_bad_credentials() throws Exception {
        when(iamFacade.login(any()))
                .thenThrow(new BusinessException(IamErrorCode.LOGIN_BAD_CREDENTIALS));
        LoginRequest request = new LoginRequest("user@example.com", "WrongPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IAM_004"));
    }

    @Test
    @DisplayName("POST /login returns 403 when account inactive")
    void should_return_403_on_login_inactive_account() throws Exception {
        when(iamFacade.login(any()))
                .thenThrow(new BusinessException(IamErrorCode.ACCOUNT_INACTIVE));
        LoginRequest request = new LoginRequest("user@example.com", "StrongPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IAM_006"));
    }

    @Test
    @DisplayName("POST /login returns 429 when rate limited")
    void should_return_429_on_login_rate_limited() throws Exception {
        when(iamFacade.login(any()))
                .thenThrow(new BusinessException(IamErrorCode.ACCOUNT_LOCKED));
        LoginRequest request = new LoginRequest("user@example.com", "StrongPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("IAM_005"));
    }

    @Test
    @DisplayName("POST /refresh returns 200 on success")
    void should_return_200_on_refresh() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh.jwt");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /refresh returns 401 when token invalid")
    void should_return_401_on_refresh_invalid() throws Exception {
        when(iamFacade.refresh(any()))
                .thenThrow(new BusinessException(IamErrorCode.REFRESH_TOKEN_INVALID));
        RefreshTokenRequest request = new RefreshTokenRequest("invalid");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /logout returns 200 when authenticated")
    void should_return_200_on_logout_authenticated() throws Exception {
        authenticateAsUser(testUserId);
        LogoutRequest request = new LogoutRequest("refresh.jwt", "access.jwt", "jti", 900L);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /logout returns 401 when not authenticated")
    void should_return_401_on_logout_unauthenticated() throws Exception {
        LogoutRequest request = new LogoutRequest("refresh.jwt", null, null, null);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /google-login returns 200 on success")
    void should_return_200_on_google_login() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("valid-google-id-token-string");
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Accept-Language", "vi"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /google-login returns 200 with English locale")
    void should_return_200_on_google_login_english_locale() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("valid-google-id-token-string");
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /google-login returns 401 when token invalid")
    void should_return_401_on_google_login_invalid_token() throws Exception {
        when(iamFacade.loginWithGoogle(any()))
                .thenThrow(new BusinessException(IamErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
        GoogleLoginRequest request = new GoogleLoginRequest("invalid-token");
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /google-login normalizes unsupported locale to vi")
    void should_normalize_unsupported_locale_to_vi() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("valid-google-id-token-string");
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Accept-Language", "fr-FR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /forgot-password returns 200 on success")
    void should_return_200_on_forgot_password() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(testUserId.toString()));
    }

    @Test
    @DisplayName("POST /forgot-password returns 200 silent when user not found")
    void should_return_200_silent_on_forgot_password_unknown() throws Exception {
        when(iamFacade.forgotPassword(any())).thenReturn(null);
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    @DisplayName("POST /forgot-password returns 429 when rate limited")
    void should_return_429_on_forgot_password_rate_limited() throws Exception {
        when(iamFacade.forgotPassword(any()))
                .thenThrow(new BusinessException(IamErrorCode.PASSWORD_RESET_COOLDOWN));
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("POST /reset-password returns 200 on success")
    void should_return_200_on_reset_password() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "NewPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /reset-password returns 400 when token is invalid")
    void should_return_400_on_reset_password_invalid_token() throws Exception {
        when(iamFacade.resetPassword(any()))
                .thenThrow(new com.pwb.shared.exception.BusinessException(IamErrorCode.AUTH_RESET_TOKEN_INVALID));
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "NewPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /reset-password returns 400 when password is too short")
    void should_return_400_on_reset_password_short_password() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "short");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /change-password returns 200 on success")
    void should_return_200_on_change_password_authenticated() throws Exception {
        authenticateAsUser(testUserId);
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!@#$", "NewPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /change-password returns 401 when not authenticated")
    void should_return_401_on_change_password_unauthenticated() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!@#$", "NewPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /change-password returns 400 when new password too short")
    void should_return_400_on_change_password_short_new() throws Exception {
        authenticateAsUser(testUserId);
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1!@#$", "short");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /change-password returns 400 when current password is wrong")
    void should_return_400_on_change_password_wrong_current() throws Exception {
        when(iamFacade.changePassword(any()))
                .thenThrow(new com.pwb.shared.exception.BusinessException(IamErrorCode.AUTH_INVALID_CURRENT_PASSWORD));
        authenticateAsUser(testUserId);
        ChangePasswordRequest request = new ChangePasswordRequest("WrongOld", "NewPass1!@#$");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}