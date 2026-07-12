package com.pwb.backend.modules.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.common.security.captcha.CaptchaVerifier;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.common.security.jwt.BearerTokenExtractor;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.dto.response.UserInfo;
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieWriter cookieWriter;

    @Mock
    private HttpClientContextResolver clientContextResolver;

    @Mock
    private BearerTokenExtractor bearerTokenExtractor;

    @Mock
    private CaptchaVerifier captchaVerifier;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AuthController authController;

    private final String email = "test@example.com";
    private final String password = "Password@123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
    }

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, "Test Name", "captcha_token");
        RegisterResponse response = new RegisterResponse(email, "PENDING_VERIFICATION", Instant.now().plusSeconds(300));

        when(authService.register(any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Registration initiated");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));

        verify(captchaVerifier).verifyOrThrow(eq("captcha_token"), any());
    }

    @Test
    void register_validationError() throws Exception {
        RegisterRequest request = new RegisterRequest("invalid-email", "", "Test Name", "captcha_token");
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Validation failed");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verifyOtp_success() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest(email, "123456");
        VerifyOtpResponse response = new VerifyOtpResponse(UUID.randomUUID(), email, "USER", "access_token", Instant.now().plusSeconds(900));

        when(authService.verifyOtp(any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("OTP verified");

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access_token"));
    }

    @Test
    void resendOtp_success() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest(email, "captcha_token");
        ResendOtpResponse response = new ResendOtpResponse(email, Instant.now().plusSeconds(60));

        when(authService.resendOtp(any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("OTP resent");

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest(email, password, "captcha_token");
        UserInfo userInfo = new UserInfo(UUID.randomUUID(), email, "Test Name", "USER", "ACTIVE", null);
        LoginResponse response = new LoginResponse(
                "access_token", 900L, Instant.now().plusSeconds(900), userInfo, "refresh_token", 604800L, null, null
        );

        when(authService.login(any(), any(), any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Login successful");
        when(clientContextResolver.resolveIp(any())).thenReturn("127.0.0.1");
        when(clientContextResolver.resolveUserAgent(any())).thenReturn("Mozilla");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access_token"));
    }

    @Test
    void loginGoogle_success() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("google_id_token", "nonce");
        UserInfo userInfo = new UserInfo(UUID.randomUUID(), email, "Test Name", "USER", "ACTIVE", null);
        LoginResponse response = new LoginResponse(
                "access_token", 900L, Instant.now().plusSeconds(900), userInfo, "refresh_token", 604800L, null, null
        );

        when(authService.loginWithGoogle(any(), any(), any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Google login successful");

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access_token"));
    }

    @Test
    void refresh_success() throws Exception {
        RefreshResponse response = new RefreshResponse("new_access_token", 900L, Instant.now().plusSeconds(900), "new_refresh_token", 604800L);

        when(bearerTokenExtractor.extractOrThrow(any())).thenReturn("expired_access_token");
        when(cookieWriter.readRefreshCookie(any())).thenReturn("old_refresh_token");
        when(clientContextResolver.resolveIp(any())).thenReturn("127.0.0.1");
        when(clientContextResolver.resolveUserAgent(any())).thenReturn("Mozilla");
        when(authService.refresh(any(), any(), any(), any())).thenReturn(response);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Token refreshed");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer expired_access_token")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old_refresh_token")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new_access_token"));
    }

    @Test
    void logout_success() throws Exception {
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Logout successful");
        when(bearerTokenExtractor.extract(any())).thenReturn("access_token");
        when(cookieWriter.readRefreshCookie(any())).thenReturn("refresh_token");
        when(clientContextResolver.resolveIp(any())).thenReturn("127.0.0.1");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer access_token")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh_token")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(eq("access_token"), eq("refresh_token"), eq("127.0.0.1"));
    }

    @Test
    void refresh_withNullRefreshCookie_throwsInvalidRefreshToken() throws Exception {
        when(cookieWriter.readRefreshCookie(any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_withMissingBearerToken_throwsUnauthorized() throws Exception {
        doThrow(new com.pwb.backend.common.exception.BusinessException(
                com.pwb.backend.common.exception.CommonErrorCode.UNAUTHORIZED))
                .when(bearerTokenExtractor).extractOrThrow(any());

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
