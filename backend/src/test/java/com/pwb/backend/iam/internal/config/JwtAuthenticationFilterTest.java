package com.pwb.backend.iam.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

  private JwtService jwtService;
  private StringRedisTemplate redisTemplate;
  private RestAuthenticationEntryPoint authenticationEntryPoint;
  private JwtAuthenticationFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    redisTemplate = mock(StringRedisTemplate.class);
    authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
    filter = new JwtAuthenticationFilter(jwtService, redisTemplate, authenticationEntryPoint);
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_noAuthorizationHeader_passesThrough() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, resp, chain);

    verify(chain, times(1)).doFilter(req, resp);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void doFilter_blacklistedToken_returns401() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer valid.jwt.sig");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature("valid.jwt.sig")).thenReturn("sig");
    when(redisTemplate.hasKey("session:blacklist_token:sig")).thenReturn(true);

    filter.doFilter(req, resp, chain);

    assertEquals(401, resp.getStatus());
    verify(chain, never()).doFilter(req, resp);
    assertNotNull(resp.getContentAsString());
    assertTrue(resp.getContentType().startsWith("application/json"));
  }

  @Test
  void doFilter_validToken_setsAuthentication() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer valid.jwt.sig");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature("valid.jwt.sig")).thenReturn("sig");
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(jwtService.isTokenValid("valid.jwt.sig")).thenReturn(true);
    when(jwtService.extractEmail("valid.jwt.sig")).thenReturn("test@gmail.com");
    when(jwtService.extractRole("valid.jwt.sig")).thenReturn("USER");

    filter.doFilter(req, resp, chain);

    verify(chain, times(1)).doFilter(req, resp);
    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals("test@gmail.com", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
  }

  @Test
  void doFilter_invalidTokenSignature_returns401() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer bad.jwt.sig");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature("bad.jwt.sig")).thenReturn("sig");
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(jwtService.isTokenValid("bad.jwt.sig")).thenReturn(false);

    filter.doFilter(req, resp, chain);

    assertEquals(401, resp.getStatus());
    verify(chain, never()).doFilter(req, resp);
  }

  @Test
  void errorBody_isJsonApiResponse() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer x.y.z");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature("x.y.z")).thenReturn("z");
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    filter.doFilter(req, resp, chain);

    ApiResponse<?> parsed = objectMapper.readValue(resp.getContentAsString(), ApiResponse.class);
    assertEquals(false, parsed.success());
  }
}