package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

  private static final String VALID_SECRET = "test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa";

  private JwtService jwtService;
  private User user;

  @BeforeEach
  void setUp() {
    IamProperties props = new IamProperties();
    props.getJwt().setSecret(VALID_SECRET);
    props.getJwt().setAccessTokenExpiration(900L);
    props.getJwt().setRefreshTokenExpiration(2592000L);
    jwtService = new JwtService(props);
    jwtService.validateSecret();

    user = new User();
    user.setId("user-uuid");
    user.setEmail("test@gmail.com");
    user.setUsername("testuser");
    Role role = new Role();
    role.setName("USER");
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE);
  }

  @Test
  void validateSecret_blankSecret_throws() {
    IamProperties props = new IamProperties();
    props.getJwt().setSecret(" ");
    JwtService svc = new JwtService(props);
    IllegalStateException ex = assertThrows(IllegalStateException.class, svc::validateSecret);
    assertTrue(ex.getMessage().contains("JWT_SECRET is required"));
  }

  @Test
  void validateSecret_shortSecret_throws() {
    IamProperties props = new IamProperties();
    props.getJwt().setSecret("short");
    JwtService svc = new JwtService(props);
    IllegalStateException ex = assertThrows(IllegalStateException.class, svc::validateSecret);
    assertTrue(ex.getMessage().contains("at least 32 bytes"));
  }

  @Test
  void generateAccessToken_andParse_returnsEmailAndRole() {
    String token = jwtService.generateAccessToken(user);
    assertNotNull(token);
    assertTrue(jwtService.isTokenValid(token));
    assertEquals("test@gmail.com", jwtService.extractEmail(token));
    assertEquals("USER", jwtService.extractRole(token));
  }

  @Test
  void generateAccessToken_uniqueJtiPerToken() {
    String a = jwtService.generateAccessToken(user);
    String b = jwtService.generateAccessToken(user);
    assertNotEquals(a, b);
  }

  @Test
  void generateRefreshToken_validAsRefreshType() {
    String refresh = jwtService.generateRefreshToken(user);
    assertTrue(jwtService.isRefreshTokenValid(refresh));
    String jti = jwtService.extractJti(refresh);
    assertNotNull(jti);
    assertFalse(jti.isBlank());
  }

  @Test
  void isRefreshTokenValid_accessTokenRejected() {
    String access = jwtService.generateAccessToken(user);
    assertFalse(jwtService.isRefreshTokenValid(access));
  }

  @Test
  void isTokenValid_garbageToken_returnsFalse() {
    assertFalse(jwtService.isTokenValid("not.a.jwt"));
    assertFalse(jwtService.isTokenValid("garbage"));
  }

  @Test
  void extractClaimsFromExpiredToken_stillReturnsSubject() {
    String token = jwtService.generateAccessToken(user);
    String email = jwtService.extractEmailFromExpiredToken(token);
    assertEquals("test@gmail.com", email);
  }

  @Test
  void getSignature_returnsPartAfterLastDot() {
    String token = jwtService.generateAccessToken(user);
    String signature = jwtService.getSignature(token);
    assertNotNull(signature);
    assertFalse(signature.contains("."));
    assertEquals(token.substring(token.lastIndexOf('.') + 1), signature);
  }

  @Test
  void getSignature_nullInput_returnsNull() {
    assertNull(jwtService.getSignature(null));
  }
}