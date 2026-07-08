package com.pwb.backend.iam.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.internal.config.JwtAuthenticationFilter;
import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.iam.internal.service.SessionService;
import com.pwb.backend.shared.security.IpRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "app.security.cors.allowed-origins=http://localhost:3000",
    "JWT_SECRET=test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa"
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean // Replaces @MockBean in Spring Boot 4.0+ / Spring 7.0+
    private AuthService authService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private AccountLifecycleService accountLifecycleService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private IpRateLimitFilter ipRateLimitFilter;

    @Test
    void testRegister_validRequest_returns200() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "testuser", "test@gmail.com", "Password@123", "Password@123", "Test User");

        RegisterResponse mockResponse = new RegisterResponse(
                "testuser", "test@gmail.com", "Test User", "PENDING_VERIFICATION");

        when(authService.register(any(RegisterRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Registration successful, please check your email for OTP verification"))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));
    }

    @Test
    void testRegister_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "testuser", "invalid-email", "Password@123", "Password@123", "Test User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void testRegister_passwordsDoNotMatch_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "testuser", "test@gmail.com", "Password@123", "Different@123", "Test User");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void testVerifyOtp_validRequest_returns200() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

        VerifyOtpResponse.UserInfo userInfo = new VerifyOtpResponse.UserInfo(
                "testuser", "test@gmail.com", "Test User", "ACTIVE");
        VerifyOtpResponse mockResponse = new VerifyOtpResponse("access-token", 900L, userInfo);

        when(authService.verifyOtp(any(VerifyOtpRequest.class), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"));
    }

    @Test
    void testResendOtp_validRequest_returns200() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest("test@gmail.com");

        mockMvc.perform(post("/api/v1/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP has been sent successfully, please check your email"));
    }
}
