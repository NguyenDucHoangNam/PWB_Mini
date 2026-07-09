package com.pwb.backend.iam.internal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.pwb.backend.iam.api.dto.request.DeleteAccountRequest;
import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OAuthProvider;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.factory.OutboxEventFactory;
import com.pwb.backend.iam.internal.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.mapper.UserMapper;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceTest {

  private static final String EMAIL = "test@gmail.com";
  private static final String USER_ID = "user-uuid";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SessionService sessionService;
  @Mock private JwtService jwtService;
  @Mock private OutboxEventFactory outboxEventFactory;
  @Mock private UserMapper userMapper;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private LoginLockoutHelper loginLockoutHelper;
  @Mock private GoogleIdTokenVerifier googleVerifier;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private HttpServletResponse httpResponse;

  private IamProperties iamProperties;
  private AccountLifecycleService service;

  @BeforeEach
  void setUp() {
    iamProperties = new IamProperties();
    service = new AccountLifecycleService(
        userRepository, passwordEncoder, redisTemplate, sessionService,
        jwtService, iamProperties, outboxEventFactory, userMapper,
        transactionManager, loginLockoutHelper);
    ReflectionTestUtils.setField(service, "googleVerifier", googleVerifier);

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    lenient().doNothing().when(transactionManager).commit(any());
    lenient().doNothing().when(transactionManager).rollback(any());

    LocaleContextHolder.setLocale(Locale.ENGLISH);

    lenient().when(outboxEventFactory.accountDeletionRequested(any(User.class), anyString(), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.accountDeletionCancelled(any(User.class), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.accountAnonymized(anyString(), anyString(), anyString()))
        .thenReturn(new OutboxEvent());
  }

  private User newActiveUser() {
    User u = new User();
    u.setId(USER_ID);
    u.setEmail(EMAIL);
    u.setUsername("testuser");
    u.setStatus(UserStatus.ACTIVE);
    u.setPassword("hashed-password");
    Role r = new Role();
    r.setName("USER");
    u.setRole(r);
    return u;
  }

  private User newOauthUser() {
    User u = newActiveUser();
    u.setPassword(null);
    u.setOauthProvider(OAuthProvider.GOOGLE);
    u.setOauthId("google-sub-123");
    return u;
  }

  private void wireTransactionCallback() {
  }

  @Test
  void deleteAccount_localUser_correctPassword_promotesPendingDeletion() {
    User user = newActiveUser();
    DeleteAccountRequest request = new DeleteAccountRequest("Password@123", null);

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password@123", "hashed-password")).thenReturn(true);
    when(userRepository.save(any(User.class))).thenReturn(user);
    wireTransactionCallback();

    service.deleteAccount(request, "Bearer valid-access", "refresh-token", httpResponse);

    assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
    assertNotNull(user.getDeletionRequestedAt());
    verify(userRepository).save(user);
    verify(outboxEventFactory).accountDeletionRequested(eq(user), anyString(), eq("en"));
    verify(sessionService).revokeAllUserSessions(USER_ID);
    verify(redisTemplate).delete("user:last_login:" + USER_ID);
    verify(sessionService).clearRefreshCookie(httpResponse);
  }

  @Test
  void deleteAccount_localUser_wrongPassword_throwsAndRecordsFailure() {
    User user = newActiveUser();
    DeleteAccountRequest request = new DeleteAccountRequest("WrongPassword", null);

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    lenient().when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.deleteAccount(request, "Bearer valid-access", null, httpResponse));
    assertEquals(ErrorCode.INVALID_PASSWORD, ex.getErrorCode());
    verify(loginLockoutHelper).recordFailure(redisTemplate, USER_ID);
    verify(outboxEventFactory, never()).accountDeletionRequested(any(User.class), anyString(), anyString());
  }

  @Test
  void deleteAccount_alreadyPendingDeletion_throws() {
    User user = newActiveUser();
    user.setStatus(UserStatus.PENDING_DELETION);
    DeleteAccountRequest request = new DeleteAccountRequest("Password@123", null);

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.deleteAccount(request, "Bearer valid-access", null, httpResponse));
    assertEquals(ErrorCode.DELETION_ALREADY_REQUESTED, ex.getErrorCode());
    verify(userRepository, never()).save(any(User.class));
    verify(outboxEventFactory, never()).accountDeletionRequested(any(User.class), anyString(), anyString());
  }

  @Test
  void deleteAccount_oauthUser_missingIdToken_throws() throws Exception {
    User user = newOauthUser();
    DeleteAccountRequest request = new DeleteAccountRequest(null, null);

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.deleteAccount(request, "Bearer valid-access", null, httpResponse));
    assertEquals(ErrorCode.INVALID_OAUTH_TOKEN, ex.getErrorCode());
    verify(googleVerifier, never()).verify(anyString());
  }

  @Test
  void deleteAccount_oauthUser_invalidIdToken_throws() throws Exception {
    User user = newOauthUser();
    DeleteAccountRequest request = new DeleteAccountRequest(null, "fake-idtoken");

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(googleVerifier.verify("fake-idtoken")).thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.deleteAccount(request, "Bearer valid-access", null, httpResponse));
    assertEquals(ErrorCode.INVALID_OAUTH_TOKEN, ex.getErrorCode());
  }

  @Test
  void deleteAccount_oauthUser_validIdToken_promotesPendingDeletion() throws Exception {
    User user = newOauthUser();
    DeleteAccountRequest request = new DeleteAccountRequest(null, "valid-idtoken");

    when(jwtService.extractEmailFromExpiredToken("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(googleVerifier.verify("valid-idtoken")).thenReturn(org.mockito.Mockito.mock(GoogleIdToken.class));
    when(userRepository.save(any(User.class))).thenReturn(user);
    wireTransactionCallback();

    service.deleteAccount(request, "Bearer valid-access", null, httpResponse);

    assertEquals(UserStatus.PENDING_DELETION, user.getStatus());
    verify(outboxEventFactory).accountDeletionRequested(eq(user), anyString(), eq("en"));
  }

  @Test
  void deleteAccount_missingAuthHeader_throws() {
    DeleteAccountRequest request = new DeleteAccountRequest("Password@123", null);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.deleteAccount(request, null, null, httpResponse));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  void cancelDeletion_pendingDeletion_reactivatesAndReturnsProfile() {
    User user = newActiveUser();
    user.setStatus(UserStatus.PENDING_DELETION);
    user.setDeletionRequestedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    UserProfileResponse profile = new UserProfileResponse(
        "testuser", EMAIL, "Test User", "USER", "ACTIVE", null, null, "LOCAL", null);

    when(jwtService.isTokenValid("valid-access")).thenReturn(true);
    when(jwtService.extractEmail("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenReturn(user);
    when(userMapper.toUserProfileResponse(any(User.class))).thenReturn(profile);

    UserProfileResponse result = service.cancelDeletion("Bearer valid-access");

    assertEquals(UserStatus.ACTIVE, user.getStatus());
    assertNull(user.getDeletionRequestedAt());
    assertNotNull(result);
    verify(outboxEventFactory).accountDeletionCancelled(eq(user), eq("en"));
  }

  @Test
  void cancelDeletion_notPending_throws() {
    User user = newActiveUser();
    when(jwtService.isTokenValid("valid-access")).thenReturn(true);
    when(jwtService.extractEmail("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.cancelDeletion("Bearer valid-access"));
    assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void cancelDeletion_gracePeriodExpired_throws() {
    User user = newActiveUser();
    user.setStatus(UserStatus.PENDING_DELETION);
    user.setDeletionRequestedAt(Instant.now().minus(31, ChronoUnit.DAYS));
    when(jwtService.isTokenValid("valid-access")).thenReturn(true);
    when(jwtService.extractEmail("valid-access")).thenReturn(EMAIL);
    when(userRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.cancelDeletion("Bearer valid-access"));
    assertTrue(ex.getMessage().contains("30-day grace period"));
  }

  @Test
  void triggerAnonymization_noUsers_returnsZero() {
    when(userRepository.findUsersPendingDeletionBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(Collections.emptyList());

    TriggerAnonymizationResponse response = service.triggerAnonymization();

    assertEquals(0, response.processedUsersCount());
    assertEquals("COMPLETED", response.status());
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void triggerAnonymization_processesEachUserIndependently() {
    User u1 = newActiveUser();
    u1.setId("user-1");
    User u2 = newActiveUser();
    u2.setId("user-2");

    when(userRepository.findUsersPendingDeletionBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(u1, u2));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    wireTransactionCallback();

    TriggerAnonymizationResponse response = service.triggerAnonymization();

    assertEquals(2, response.processedUsersCount());
    verify(userRepository).save(u1);
    verify(userRepository).save(u2);
    verify(sessionService).revokeAllUserSessions("user-1");
    verify(sessionService).revokeAllUserSessions("user-2");
    verify(loginLockoutHelper, org.mockito.Mockito.times(2)).clear(any(StringRedisTemplate.class), anyString());
    verify(outboxEventFactory, org.mockito.Mockito.times(2))
        .accountAnonymized(anyString(), anyString(), eq("en"));
  }

  @Test
  void triggerAnonymization_partialFailure_continuesRemaining() {
    User u1 = newActiveUser();
    u1.setId("user-1");
    User u2 = newActiveUser();
    u2.setId("user-2");

    when(userRepository.findUsersPendingDeletionBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(u1, u2));
    lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    wireTransactionCallback();
    doThrow(new RuntimeException("boom")).when(sessionService).revokeAllUserSessions("user-1");
    doNothing().when(sessionService).revokeAllUserSessions("user-2");

    TriggerAnonymizationResponse response = service.triggerAnonymization();

    assertEquals(1, response.processedUsersCount());
    verify(sessionService).revokeAllUserSessions("user-2");
  }

  @Test
  void anonymizeUser_setsFieldsAndEmitsEvent() throws Exception {
    User u = newActiveUser();
    wireTransactionCallback();
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    java.lang.reflect.Method m = AccountLifecycleService.class.getDeclaredMethod("anonymizeUser", User.class);
    m.setAccessible(true);
    m.invoke(service, u);

    assertEquals(UserStatus.ANONYMIZED, u.getStatus());
    assertTrue(u.getEmail().startsWith("deleted_"));
    assertTrue(u.getEmail().endsWith("@pwbmini.com"));
    assertNull(u.getPassword());
    assertNull(u.getFullName());
    assertNull(u.getPhone());
    assertNull(u.getAvatarUrl());
    assertNull(u.getOauthProvider());
    assertNull(u.getOauthId());
    verify(userRepository).save(u);
    verify(sessionService).revokeAllUserSessions(USER_ID);
    verify(loginLockoutHelper).clear(redisTemplate, USER_ID);
    verify(outboxEventFactory).accountAnonymized(eq(USER_ID), anyString(), eq("en"));
  }
}
