package com.pwb.backend.modules.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.common.security.CurrentUserResolver;
import com.pwb.backend.common.security.captcha.CaptchaVerifier;
import com.pwb.backend.common.security.cookie.RefreshTokenCookieWriter;
import com.pwb.backend.modules.iam.dto.request.ChangePasswordRequest;
import com.pwb.backend.modules.iam.dto.request.ForgotPasswordRequest;
import com.pwb.backend.modules.iam.dto.request.ResetPasswordRequest;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.service.PasswordChangeService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private PasswordChangeService passwordChangeService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private RefreshTokenCookieWriter cookieWriter;

    @Mock
    private CaptchaVerifier captchaVerifier;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PasswordController passwordController;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
    }

    @Test
    void forgotPassword_success() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com", "captcha_token");

        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Reset link sent");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(captchaVerifier).verifyOrThrow(eq("captcha_token"), any());
        verify(passwordChangeService).requestPasswordReset("test@example.com");
    }

    @Test
    void resetPassword_success() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "NewPassword@123", "NewPassword@123");

        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Reset successful");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordChangeService).resetPassword("reset-token", "NewPassword@123");
    }

    @Test
    void changePassword_success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123");

        when(currentUserResolver.resolveUserId()).thenReturn(userId);
        when(cookieWriter.readRefreshCookie(any())).thenReturn("current_refresh_token");
        when(passwordChangeService.changePassword(eq(userId), eq("OldPassword@123"), eq("NewPassword@123"), eq("current_refresh_token")))
                .thenReturn(2);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Password changed");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revokedOtherSessions").value(2));
    }

    @Test
    void changePassword_withNullUserId_throwsUnauthorized() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123");

        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(currentUserResolver).resolveUserId();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
