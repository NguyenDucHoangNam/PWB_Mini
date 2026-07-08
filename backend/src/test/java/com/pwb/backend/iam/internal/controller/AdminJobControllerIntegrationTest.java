package com.pwb.backend.iam.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.internal.config.JwtAuthenticationFilter;
import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.security.IpRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminJobController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "app.security.cors.allowed-origins=http://localhost:3000",
    "JWT_SECRET=test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa"
})
class AdminJobControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AccountLifecycleService accountLifecycleService;

  @MockitoBean
  private JwtService jwtService;

  @MockitoBean
  private StringRedisTemplate redisTemplate;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @MockitoBean
  private IpRateLimitFilter ipRateLimitFilter;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testTriggerAnonymization_success() throws Exception {
    TriggerAnonymizationResponse mockResponse = new TriggerAnonymizationResponse(5, 120, "COMPLETED");
    when(accountLifecycleService.triggerAnonymization()).thenReturn(mockResponse);

    mockMvc.perform(post("/api/v1/admin/jobs/trigger-anonymization")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.processedUsersCount").value(5))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"));
  }
}
