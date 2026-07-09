package com.pwb.backend.iam.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.iam.internal.service.AvatarUploadService;
import com.pwb.backend.iam.internal.service.SessionService;
import com.pwb.backend.shared.security.IpRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerRateLimitFilterIntegrationTest {

  private AuthService authService;
  private SessionService sessionService;
  private AccountLifecycleService accountLifecycleService;
  private AvatarUploadService avatarUploadService;
  private MessageSource messageSource;
  private StringRedisTemplate redisTemplate;
  private IpRateLimitFilter rateLimitFilter;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    sessionService = mock(SessionService.class);
    accountLifecycleService = mock(AccountLifecycleService.class);
    avatarUploadService = mock(AvatarUploadService.class);
    messageSource = mock(MessageSource.class);
    redisTemplate = mock(StringRedisTemplate.class);
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(ops);

    rateLimitFilter = new IpRateLimitFilter(redisTemplate,
        new com.pwb.backend.shared.security.ClientIpResolver("127.0.0.1,::1"), 2, 60);

    AuthController controller = new AuthController(authService, sessionService, accountLifecycleService, avatarUploadService, messageSource);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .addFilters(rateLimitFilter)
        .build();
  }

  @Test
  void request_underLimit_passesFilter() throws Exception {
    when(redisTemplate.opsForValue().increment(anyString())).thenReturn(1L);
    when(authService.login(any(LoginRequest.class), any())).thenReturn(null);

    ObjectMapper om = new ObjectMapper();
    LoginRequest req = new LoginRequest("testuser", "Password@123");
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(req)))
        .andExpect(status().isOk());

    verify(redisTemplate, atLeast(1)).opsForValue();
  }

  @Test
  void request_overLimit_returns429() throws Exception {
    when(redisTemplate.opsForValue().increment(anyString())).thenReturn(99L);

    ObjectMapper om = new ObjectMapper();
    LoginRequest req = new LoginRequest("testuser", "Password@123");
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(req)))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void requestToNonAuthEndpoint_skipsFilter() throws Exception {
    AuthController controller = new AuthController(authService, sessionService, accountLifecycleService, avatarUploadService, messageSource);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .addFilters(rateLimitFilter)
        .build();

    mockMvc.perform(post("/api/v1/users/active-sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound());

    verify(redisTemplate, org.mockito.Mockito.never()).opsForValue();
  }
}
