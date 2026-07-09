package com.pwb.backend.iam.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.iam.internal.service.JwtEpochService;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.response.ApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

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

  private static final String SECRET = "test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa";

  private JwtService jwtService;
  private JwtEpochService jwtEpochService;
  private StringRedisTemplate redisTemplate;
  private RestAuthenticationEntryPoint authenticationEntryPoint;
  private JwtAuthenticationFilter filter;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    jwtEpochService = mock(JwtEpochService.class);
    redisTemplate = mock(StringRedisTemplate.class);
    authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
    filter = new JwtAuthenticationFilter(jwtService, jwtEpochService, redisTemplate, authenticationEntryPoint);
    SecurityContextHolder.clearContext();

    // The filter uses the real signing key when re-parsing claims to read
    // the epoch claim. Provide a real key from a known test secret so the
    // generated tokens verify.
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    when(jwtService.getSigningKeyForFilter()).thenReturn(key);
    when(jwtEpochService.currentEpoch()).thenReturn(1L);
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
    String token = generateToken(Map.of("email", "test@gmail.com"), 1L);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature(token)).thenReturn("sig");
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(jwtService.isTokenValid(token)).thenReturn(true);
    when(jwtService.extractEmail(token)).thenReturn("test@gmail.com");
    when(jwtService.extractRole(token)).thenReturn("USER");

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
  void doFilter_staleEpochToken_returns401() throws Exception {
    // Token was issued at epoch 1, but operator has rotated to epoch 5.
    String token = generateToken(Map.of("email", "test@gmail.com"), 1L);
    when(jwtEpochService.currentEpoch()).thenReturn(5L);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    when(jwtService.getSignature(token)).thenReturn("sig");
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(jwtService.isTokenValid(token)).thenReturn(true);

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

  private String generateToken(Map<String, Object> claims, long epoch) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    Instant now = Instant.now();
    return Jwts.builder()
        .subject((String) claims.get("email"))
        .claim("role", "USER")
        .claim(JwtService.CLAIM_EPOCH, epoch)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(900)))
        .signWith(key)
        .compact();
  }
}