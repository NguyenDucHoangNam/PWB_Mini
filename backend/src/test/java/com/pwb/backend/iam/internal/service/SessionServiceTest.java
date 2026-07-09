package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.security.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String EMAIL = "test@gmail.com";
  private static final String USER_ID = "user-uuid";
  private static final long REFRESH_EXP = 604800L;
  private static final long ACCESS_EXP = 900L;

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private UserRepository userRepository;
  @Mock private JwtService jwtService;
  @Mock private GeoIpService geoIpService;
  @Mock private RedisScript<List<String>> concurrentSessionScript;
  @Mock private RedisScript<String> sessionRotationScript;
  @Mock private RedisScript<List<String>> revokeOtherSessionsScript;
  @Mock private LoginLockoutHelper loginLockoutHelper;
  @Mock private ClientIpResolver clientIpResolver;
  @Mock private HttpServletResponse httpResponse;

  private IamProperties iamProperties;
  private SessionService sessionService;
  private ValueOperations<String, String> valueOps;
  private HashOperations<String, Object, Object> hashOps;
  private ZSetOperations<String, String> zsetOps;

  @BeforeEach
  void setUp() {
    iamProperties = new IamProperties();
    iamProperties.getJwt().setAccessTokenExpiration(ACCESS_EXP);
    iamProperties.getJwt().setRefreshTokenExpiration(REFRESH_EXP);

    clientIpResolver = org.mockito.Mockito.mock(ClientIpResolver.class);
    lenient().when(clientIpResolver.current()).thenReturn("127.0.0.1");

    sessionService = new SessionService(
        redisTemplate, userRepository, jwtService, geoIpService,
        iamProperties, concurrentSessionScript, sessionRotationScript,
        revokeOtherSessionsScript, loginLockoutHelper, clientIpResolver);

    valueOps = org.mockito.Mockito.mock(ValueOperations.class);
    hashOps = org.mockito.Mockito.mock(HashOperations.class);
    zsetOps = org.mockito.Mockito.mock(ZSetOperations.class);

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
    lenient().when(redisTemplate.opsForZSet()).thenReturn(zsetOps);

    lenient().doNothing().when(loginLockoutHelper)
        .ensureNotLocked(any(StringRedisTemplate.class), anyString());
    lenient().doNothing().when(loginLockoutHelper)
        .clear(any(StringRedisTemplate.class), anyString());
  }

  private User newActiveUser() {
    User u = new User();
    u.setId(USER_ID);
    u.setEmail(EMAIL);
    u.setUsername("testuser");
    u.setStatus(UserStatus.ACTIVE);
    Role r = new Role();
    r.setName("USER");
    u.setRole(r);
    return u;
  }

  private CapturedCookies captureCookies() {
    CapturedCookies captured = new CapturedCookies();
    doAnswer(invocation -> {
      Cookie c = invocation.getArgument(0);
      captured.cookies.add(c);
      return null;
    }).when(httpResponse).addCookie(any(Cookie.class));
    return captured;
  }

  private static class CapturedCookies {
    final List<Cookie> cookies = new java.util.ArrayList<>();
  }

  @Test
  void createSession_setsRefreshCookieWithCorrectMaxAge() {
    User user = newActiveUser();
    when(jwtService.generateAccessToken(user)).thenReturn("new-access");
    when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");
    when(jwtService.getSignature("new-access")).thenReturn("sig-1");
    when(geoIpService.getLocation(anyString())).thenReturn("VN");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(Collections.emptyList());

    CapturedCookies captured = captureCookies();

    sessionService.createSession(user, httpResponse);

    Cookie refreshCookie = captured.cookies.stream()
        .filter(c -> "refreshToken".equals(c.getName()))
        .findFirst().orElseThrow();
    assertTrue(refreshCookie.isHttpOnly());
    assertEquals("/", refreshCookie.getPath());
    assertEquals((int) REFRESH_EXP, refreshCookie.getMaxAge());
    assertEquals("new-refresh", refreshCookie.getValue());
  }

  @Test
  void createSession_storesSessionKeyWithUserId() {
    User user = newActiveUser();
    when(jwtService.generateAccessToken(user)).thenReturn("new-access");
    when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");
    when(jwtService.getSignature(anyString())).thenReturn("sig-1");
    when(geoIpService.getLocation(anyString())).thenReturn("VN");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(Collections.emptyList());
    captureCookies();

    sessionService.createSession(user, httpResponse);

    verify(valueOps).set(eq("session:refresh_token:new-refresh"), eq(USER_ID), eq(java.time.Duration.ofSeconds(REFRESH_EXP)));
  }

  @Test
  void createSession_writesSessionMetadata() {
    User user = newActiveUser();
    when(jwtService.generateAccessToken(user)).thenReturn("new-access");
    when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");
    when(jwtService.getSignature("new-access")).thenReturn("sig-1");
    when(geoIpService.getLocation("127.0.0.1")).thenReturn("VN");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(Collections.emptyList());
    captureCookies();

    sessionService.createSession(user, httpResponse);

    verify(hashOps).putAll(eq("session:metadata:new-refresh"), any(Map.class));
    verify(redisTemplate).expire(eq("session:metadata:new-refresh"), eq(REFRESH_EXP), any(java.util.concurrent.TimeUnit.class));
  }

  @Test
  void createSession_kicksOldestWhenLimitExceeded() {
    User user = newActiveUser();
    when(jwtService.generateAccessToken(user)).thenReturn("new-access");
    when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");
    when(jwtService.getSignature(anyString())).thenReturn("sig-1");
    when(geoIpService.getLocation(anyString())).thenReturn("VN");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(List.of("old-token-1", "old-token-2"));
    captureCookies();

    sessionService.createSession(user, httpResponse);

    verify(redisTemplate).delete("session:refresh_token:old-token-1");
    verify(redisTemplate).delete("session:refresh_token:old-token-2");
  }

  @Test
  void refresh_happyPath_rotatesTokenAndReturnsNewAccess() {
    User user = newActiveUser();
    String refresh = "old-refresh";
    String newRefresh = "new-refresh";
    String newAccess = "new-access";
    String oldAccess = "old-access";
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    lenient().when(jwtService.extractEmailFromExpiredToken(anyString())).thenReturn(EMAIL);
    when(valueOps.get("session:refresh_token:" + refresh)).thenReturn(USER_ID);
    when(jwtService.isRefreshTokenValid(refresh)).thenReturn(true);
    when(jwtService.extractJti(refresh)).thenReturn("jti-1");
    when(jwtService.generateAccessToken(user)).thenReturn(newAccess);
    when(jwtService.generateRefreshToken(user)).thenReturn(newRefresh);
    when(jwtService.getSignature(newAccess)).thenReturn("new-sig");
    when(redisTemplate.hasKey("session:metadata:" + refresh)).thenReturn(false);
    when(geoIpService.getLocation(anyString())).thenReturn("VN");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenReturn(null);
    CapturedCookies captured = captureCookies();

    RefreshResponse response = sessionService.refreshAccessToken(
        "Bearer " + oldAccess, refresh, httpResponse);

    assertEquals(newAccess, response.accessToken());
    assertEquals(ACCESS_EXP, response.expiresIn());

    Cookie cookie = captured.cookies.stream()
        .filter(c -> "refreshToken".equals(c.getName())).findFirst().orElseThrow();
    assertEquals(newRefresh, cookie.getValue());
    assertEquals((int) REFRESH_EXP, cookie.getMaxAge());
  }

  @Test
  void refresh_noHeaderNoRefreshToken_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.refreshAccessToken(null, null, httpResponse));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void refresh_expiredHeaderUsesExpiredTokenEmail() {
    User user = newActiveUser();
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(jwtService.extractEmailFromExpiredToken("expired-access")).thenReturn(EMAIL);
    when(jwtService.isRefreshTokenValid(anyString())).thenReturn(false);

    String exMessage = assertThrows(BusinessException.class,
        () -> sessionService.refreshAccessToken("Bearer expired-access", "some-refresh", httpResponse))
        .getMessage();
    assertTrue(exMessage.contains("Refresh token signature invalid"));
  }

  @Test
  void refresh_validRefreshTokenExtractsEmail() {
    User user = newActiveUser();
    when(jwtService.isRefreshTokenValid("valid-refresh")).thenReturn(true);
    when(jwtService.extractEmail("valid-refresh")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(jwtService.extractJti("valid-refresh")).thenReturn("jti-1");
    when(valueOps.get("session:refresh_token:valid-refresh")).thenReturn(null);
    when(valueOps.get("session:refresh_token:revoked:valid-refresh")).thenReturn(null);
    when(valueOps.get("session:refresh_token:shadow:valid-refresh")).thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.refreshAccessToken(null, "valid-refresh", httpResponse));
    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
  }

  @Test
  void refresh_tokenTheft_revokeAllAndThrow() {
    User user = newActiveUser();
    String refresh = "stolen-refresh";
    when(jwtService.isRefreshTokenValid(refresh)).thenReturn(true);
    when(jwtService.extractEmail(refresh)).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(jwtService.extractJti(refresh)).thenReturn("jti-1");
    when(valueOps.get("session:refresh_token:" + refresh)).thenReturn(null);
    when(valueOps.get("session:refresh_token:shadow:" + refresh)).thenReturn(null);
    when(valueOps.get("session:refresh_token:revoked:" + refresh)).thenReturn(USER_ID);

    Set<String> sessions = new LinkedHashSet<>();
    sessions.add("sess-1");
    when(zsetOps.range("user:sessions:" + USER_ID, 0, -1)).thenReturn(sessions);
    when(hashOps.get("session:metadata:sess-1", "active_jwt_signature")).thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.refreshAccessToken(null, refresh, httpResponse));
    assertEquals(ErrorCode.TOKEN_THEFT_DETECTED, ex.getErrorCode());
    verify(redisTemplate).delete(any(java.util.Collection.class));
  }

  @Test
  void getActiveSessions_extractsMetadataFromHash() {
    User user = newActiveUser();
    String authHeader = "Bearer access-token";
    when(jwtService.extractEmail("access-token")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));

    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    Map<Object, Object> meta = new HashMap<>();
    meta.put("ip", "1.2.3.4");
    meta.put("browser", "Chrome");
    meta.put("os", "Linux");
    meta.put("location", "VN");
    meta.put("createdAt", created.toString());

    String currentRefresh = "current-refresh";
    String otherRefresh = "other-refresh";
    Set<String> tokens = new LinkedHashSet<>();
    tokens.add(currentRefresh);
    tokens.add(otherRefresh);
    when(zsetOps.range("user:sessions:" + USER_ID, 0, -1)).thenReturn(tokens);
    when(hashOps.entries("session:metadata:" + currentRefresh)).thenReturn(meta);
    when(hashOps.entries("session:metadata:" + otherRefresh)).thenReturn(meta);

    List<ActiveSessionResponse> sessions = sessionService.getActiveSessions(authHeader, currentRefresh);

    assertEquals(2, sessions.size());
    ActiveSessionResponse mine = sessions.stream()
        .filter(s -> s.sessionUuid().equals(currentRefresh)).findFirst().orElseThrow();
    assertTrue(mine.isCurrent());
    assertEquals("1.2.3.4", mine.ipAddress());
    assertEquals("Chrome (Linux)", mine.deviceInfo());

    ActiveSessionResponse other = sessions.stream()
        .filter(s -> s.sessionUuid().equals(otherRefresh)).findFirst().orElseThrow();
    assertFalse(other.isCurrent());
  }

  @Test
  void getActiveSessions_invalidHeader_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.getActiveSessions("Basic xyz", "refresh"));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void logout_blacklistsAccessTokenAndDeletesSession() {
    User user = newActiveUser();
    String access = "access-token";
    String refresh = "refresh-token";
    when(jwtService.extractClaimsFromExpiredToken(access)).thenReturn(
        io.jsonwebtoken.Jwts.claims().setSubject(EMAIL).setExpiration(new java.util.Date(System.currentTimeMillis() + 60000)).build());
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(jwtService.getSignature(access)).thenReturn("signature-xyz");
    captureCookies();

    sessionService.logout("Bearer " + access, refresh, httpResponse);

    verify(valueOps).set(eq("session:blacklist_token:signature-xyz"), eq("true"), any(java.time.Duration.class));
    verify(redisTemplate).delete("session:refresh_token:" + refresh);
    verify(redisTemplate).delete("session:metadata:" + refresh);
    verify(zsetOps).remove("user:sessions:" + USER_ID, refresh);
  }

  @Test
  void logout_invalidHeader_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.logout(null, "refresh", httpResponse));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void revokeSession_happyPath_removesAndBlacklists() {
    User user = newActiveUser();
    String authHeader = "Bearer access-token";
    String targetUuid = "other-refresh";
    when(jwtService.extractEmail("access-token")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(zsetOps.score("user:sessions:" + USER_ID, targetUuid)).thenReturn(1234.0);
    when(hashOps.get("session:metadata:" + targetUuid, "active_jwt_signature")).thenReturn("target-sig");

    sessionService.revokeSession(targetUuid, authHeader, "current-refresh");

    verify(redisTemplate).delete("session:refresh_token:" + targetUuid);
    verify(redisTemplate).delete("session:metadata:" + targetUuid);
    verify(zsetOps).remove("user:sessions:" + USER_ID, targetUuid);
    verify(valueOps).set(eq("session:blacklist_token:target-sig"), eq("true"), any(java.time.Duration.class));
  }

  @Test
  void revokeSession_currentSession_throws() {
    User user = newActiveUser();
    String authHeader = "Bearer access-token";
    String current = "current-refresh";
    when(jwtService.extractEmail("access-token")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(zsetOps.score("user:sessions:" + USER_ID, current)).thenReturn(1234.0);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> sessionService.revokeSession(current, authHeader, current));
    assertEquals(ErrorCode.CANNOT_REVOKE_CURRENT_SESSION, ex.getErrorCode());
  }

  @Test
  void revokeOtherSessions_executeScriptAndBlacklistsReturnedSignatures() {
    User user = newActiveUser();
    String authHeader = "Bearer access-token";
    when(jwtService.extractEmail("access-token")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(redisTemplate.execute(eq(revokeOtherSessionsScript), anyList(), any(Object[].class)))
        .thenReturn(List.of("sig-A", "sig-B"));

    sessionService.revokeOtherSessions(authHeader, "current-refresh");

    verify(redisTemplate).executePipelined(any(org.springframework.data.redis.core.SessionCallback.class));
  }

  @Test
  void revokeOtherSessions_noReturnedSignatures_doesNotBlacklist() {
    User user = newActiveUser();
    String authHeader = "Bearer access-token";
    when(jwtService.extractEmail("access-token")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(redisTemplate.execute(eq(revokeOtherSessionsScript), anyList(), any(Object[].class)))
        .thenReturn(Collections.emptyList());

    sessionService.revokeOtherSessions(authHeader, "current-refresh");

    verify(redisTemplate, never()).executePipelined(any(org.springframework.data.redis.core.SessionCallback.class));
  }

  @Test
  void revokeAllUserSessions_blacklistsAndDeletesAll() {
    Set<String> sessions = new LinkedHashSet<>();
    sessions.add("sess-1");
    sessions.add("sess-2");
    when(zsetOps.range("user:sessions:" + USER_ID, 0, -1)).thenReturn(sessions);
    when(hashOps.get("session:metadata:sess-1", "active_jwt_signature")).thenReturn("sig-1");
    when(hashOps.get("session:metadata:sess-2", "active_jwt_signature")).thenReturn(null);

    sessionService.revokeAllUserSessions(USER_ID);

    verify(valueOps).set(eq("session:blacklist_token:sig-1"), eq("true"), any(java.time.Duration.class));
    verify(redisTemplate).delete(any(java.util.Collection.class));
  }

  @Test
  void revokeAllUserSessions_emptyZset_noOp() {
    when(zsetOps.range("user:sessions:" + USER_ID, 0, -1)).thenReturn(Collections.emptySet());

    sessionService.revokeAllUserSessions(USER_ID);

    verify(redisTemplate, never()).delete(any(java.util.List.class));
  }

  @Test
  void clearRefreshCookie_setsMaxAgeZero() {
    sessionService.clearRefreshCookie(httpResponse);

    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<Cookie> captor = org.mockito.ArgumentCaptor.forClass(Cookie.class);
    verify(httpResponse).addCookie(captor.capture());
    Cookie c = captor.getValue();
    assertEquals("refreshToken", c.getName());
    assertEquals(0, c.getMaxAge());
    assertEquals("", c.getValue());
    assertTrue(c.isHttpOnly());
  }
}
