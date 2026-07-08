package com.pwb.backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpRateLimitFilterTest {

  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private IpRateLimitFilter filter;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    filter = new IpRateLimitFilter(redisTemplate, 3, 60);
  }

  @Test
  void shouldNotFilter_publicPath_skipsRateLimit() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/users/me");
    assertTrue(filter.shouldNotFilter(req));
  }

  @Test
  void shouldNotFilter_loginPath_runsRateLimit() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/auth/login");
    org.junit.jupiter.api.Assertions.assertFalse(filter.shouldNotFilter(req));
  }

  @Test
  void doFilter_underLimit_callsChain() throws Exception {
    HttpServletRequest req = loginRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    when(valueOperations.increment(anyString())).thenReturn(1L);

    filter.doFilter(req, response, chain);

    verify(chain, times(1)).doFilter(req, response);
    verify(redisTemplate).expire(anyString(), any(Duration.class));
  }

  @Test
  void doFilter_overLimit_blocks429() throws Exception {
    HttpServletRequest req = loginRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    when(valueOperations.increment(anyString())).thenReturn(4L);

    filter.doFilter(req, response, chain);

    verify(chain, never()).doFilter(req, response);
    org.junit.jupiter.api.Assertions.assertEquals(429, response.getStatus());
    assertTrue(response.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void doFilter_usesXForwardedFor() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/auth/login");
    when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    when(valueOperations.increment(anyString())).thenReturn(1L);

    filter.doFilter(req, response, chain);

    verify(redisTemplate).expire(eq("rate_limit:ip:203.0.113.10"), any(Duration.class));
  }

  @Test
  void doFilter_xForwardedForUnknown_usesRemoteAddr() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/auth/login");
    when(req.getHeader("X-Forwarded-For")).thenReturn("unknown");
    when(req.getRemoteAddr()).thenReturn("198.51.100.5");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    when(valueOperations.increment(anyString())).thenReturn(1L);

    filter.doFilter(req, response, chain);

    verify(redisTemplate).expire(eq("rate_limit:ip:198.51.100.5"), any(Duration.class));
  }

  private static HttpServletRequest loginRequest() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/auth/login");
    when(req.getRemoteAddr()).thenReturn("127.0.0.1");
    return req;
  }

  private static void assertTrue(boolean condition) {
    org.junit.jupiter.api.Assertions.assertTrue(condition);
  }
}