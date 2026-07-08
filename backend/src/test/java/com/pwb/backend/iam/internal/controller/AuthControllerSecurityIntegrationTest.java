package com.pwb.backend.iam.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.internal.config.JwtAuthenticationFilter;
import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.iam.internal.service.SessionService;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.security.IpRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
class AuthControllerSecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private SessionService sessionService;

  @MockitoBean
  private AccountLifecycleService accountLifecycleService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @MockitoBean
  private IpRateLimitFilter ipRateLimitFilter;

  @MockitoBean
  private StringRedisTemplate redisTemplate;

  @Test
  void testLogin_success_returns200() throws Exception {
    LoginRequest request = new LoginRequest("testuser", "Password@123");
    LoginResponse.UserInfo info = new LoginResponse.UserInfo("testuser", "test@gmail.com", "Test User", "USER", "ACTIVE");
    LoginResponse mockResponse = new LoginResponse("access-token", 900L, info);
    when(authService.login(any(LoginRequest.class), any())).thenReturn(mockResponse);

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"));
  }

  @Test
  void testLogin_badCredentials_returns400() throws Exception {
    LoginRequest request = new LoginRequest("wronguser", "WrongPass@1");
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new BusinessException(ErrorCode.BAD_CREDENTIALS, "Invalid credentials"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testLogin_accountLocked_returns423() throws Exception {
    LoginRequest request = new LoginRequest("lockeduser", "Password@123");
    when(authService.login(any(LoginRequest.class), any()))
        .thenThrow(new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, "Account is locked"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testLogin_validationFailure_returns400() throws Exception {
    LoginRequest request = new LoginRequest("", "");

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testLoginWithGoogle_success_returns200() throws Exception {
    Oauth2LoginRequest request = new Oauth2LoginRequest("google-id-token", null);
    LoginResponse.UserInfo info = new LoginResponse.UserInfo("oauthuser", "oauth@gmail.com", "OAuth User", "USER", "ACTIVE");
    LoginResponse mockResponse = new LoginResponse("access-token", 900L, info);
    when(authService.loginWithGoogle(any(Oauth2LoginRequest.class), any())).thenReturn(mockResponse);

    mockMvc.perform(post("/api/v1/auth/login/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"));
  }

  @Test
  void testLoginWithGoogle_invalidIdToken_returns400() throws Exception {
    Oauth2LoginRequest request = new Oauth2LoginRequest("invalid-token", null);
    when(authService.loginWithGoogle(any(Oauth2LoginRequest.class), any()))
        .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid Google ID token"));

    mockMvc.perform(post("/api/v1/auth/login/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testLoginWithGoogle_oauthLinkingRequiresPassword_returns409() throws Exception {
    Oauth2LoginRequest request = new Oauth2LoginRequest("google-id-token", null);
    when(authService.loginWithGoogle(any(Oauth2LoginRequest.class), any()))
        .thenThrow(new BusinessException(ErrorCode.OAUTH_LINK_PASSWORD_REQUIRED, "Password required"));

    mockMvc.perform(post("/api/v1/auth/login/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void testLoginWithGoogle_missingIdToken_returns400() throws Exception {
    Oauth2LoginRequest request = new Oauth2LoginRequest("", null);

    mockMvc.perform(post("/api/v1/auth/login/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }
}