package com.pwb.backend.iam.internal.service;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtPrincipalExtractorTest {

  private final JwtService jwtService = jwtService();

  @Test
  void requireEmailFromHeader_valid_returnsEmail() {
    User user = newUser();
    String token = jwtService.generateAccessToken(user);
    String email = JwtPrincipalExtractor.requireEmailFromHeader("Bearer " + token, jwtService);
    assertEquals("test@gmail.com", email);
  }

  @Test
  void requireEmailFromHeader_missingHeader_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> JwtPrincipalExtractor.requireEmailFromHeader(null, jwtService));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void requireEmailFromHeader_wrongPrefix_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> JwtPrincipalExtractor.requireEmailFromHeader("Basic abc", jwtService));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void requireEmailFromHeader_invalidToken_throwsUnauthorized() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> JwtPrincipalExtractor.requireEmailFromHeader("Bearer not.a.jwt", jwtService));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void requireRoleFromHeader_valid_returnsRole() {
    User user = newUser();
    String token = jwtService.generateAccessToken(user);
    String role = JwtPrincipalExtractor.requireRoleFromHeader("Bearer " + token, jwtService);
    assertEquals("USER", role);
  }

  @Test
  void requireTokenFromHeader_stripsBearer() {
    String token = JwtPrincipalExtractor.requireTokenFromHeader("Bearer abc.def.ghi");
    assertEquals("abc.def.ghi", token);
  }

  @Test
  void requireEmailFromExpiredTokenHeader_returnsEmailEvenForExpired() {
    User user = newUser();
    String token = jwtService.generateAccessToken(user);
    String email = JwtPrincipalExtractor.requireEmailFromExpiredTokenHeader("Bearer " + token, jwtService);
    assertEquals("test@gmail.com", email);
  }

  private static User newUser() {
    User u = new User();
    u.setId("user-uuid");
    u.setEmail("test@gmail.com");
    u.setUsername("testuser");
    Role r = new Role();
    r.setName("USER");
    u.setRole(r);
    u.setStatus(UserStatus.ACTIVE);
    return u;
  }

  private static JwtService jwtService() {
    IamProperties props = new IamProperties();
    props.getJwt().setSecret("test-secret-32-bytes-aaaaaaaaaaaaaaaaaaaaaaaa");
    props.getJwt().setAccessTokenExpiration(900L);
    props.getJwt().setRefreshTokenExpiration(2592000L);
    JwtEpochService epoch = new JwtEpochService(null, props);
    JwtService svc = new JwtService(props, epoch);
    svc.validateSecret();
    return svc;
  }
}