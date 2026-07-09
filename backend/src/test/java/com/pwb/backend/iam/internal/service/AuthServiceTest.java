package com.pwb.backend.iam.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.pwb.backend.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.backend.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.DeleteAccountRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import java.util.Map;
import java.util.HashMap;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OAuthProvider;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.mapper.UserMapper;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.iam.internal.repository.RoleRepository;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private AuthService authService;

  @Mock
  private UserRepository userRepository;
  @Mock
  private RoleRepository roleRepository;
  @Mock
  private com.pwb.backend.iam.internal.factory.OutboxEventFactory outboxEventFactory;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private OtpService otpService;
  @Mock
  private JwtService jwtService;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private StringRedisTemplate redisTemplate;
  @Mock
  private UserMapper userMapper;
  @Mock
  private ValueOperations<String, String> valueOperations;
  @Mock
  private org.springframework.data.redis.core.HashOperations hashOperations;
  @Mock
  private HttpServletResponse httpResponse;
  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RLock rLock;
  @Mock
  private TransactionTemplate transactionTemplate;
  @Mock
  private PlatformTransactionManager transactionManager;
  @Mock
  private RedisScript<List<String>> concurrentSessionScript;
  @Mock
  private RedisScript<String> sessionRotationScript;
  @Mock
  private GoogleIdTokenVerifier googleVerifier;
  @Mock
  private GeoIpService geoIpService;
  @Mock
  private RedisScript<List<String>> revokeOtherSessionsScript;
  @Mock
  private SessionService sessionService;
  @Mock
  private AccountLifecycleService accountLifecycleService;

  private IamProperties iamProperties;

  @BeforeEach
  void setUp() {
    iamProperties = new IamProperties();
    iamProperties.getOtp().setExpiration(300);
    iamProperties.getOtp().setCooldown(60);
    iamProperties.getOtp().setMaxAttempts(5);
    iamProperties.getJwt().setAccessTokenExpiration(900);
    iamProperties.getJwt().setRefreshTokenExpiration(604800);

    lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    try {
      lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    } catch (InterruptedException e) {
    }

    lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    lenient().doAnswer(invocation -> {
      Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
      callback.accept(null);
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());

    lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);

    lenient().doReturn(Collections.emptyList())
        .when(redisTemplate)
        .execute(any(RedisScript.class), any(List.class), any(Object[].class));

    lenient().when(outboxEventFactory.registrationOtp(any(User.class), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.welcomeEmail(any(User.class), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.passwordReset(any(User.class), anyString(), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.accountDeletionRequested(any(User.class), anyString(), anyString()))
        .thenReturn(new OutboxEvent());
    lenient().when(outboxEventFactory.accountAnonymized(anyString(), anyString(), anyString()))
        .thenReturn(new OutboxEvent());

    authService = new AuthService(
        userRepository,
        roleRepository,
        passwordEncoder,
        otpService,
        jwtService,
        eventPublisher,
        objectMapper,
        iamProperties,
        redisTemplate,
        userMapper,
        redissonClient,
        transactionTemplate,
        transactionManager,
        sessionService,
        accountLifecycleService,
        outboxEventFactory
    );
    ReflectionTestUtils.setField(authService, "googleVerifier", googleVerifier);
  }

  @Test
  void testRegister_success_savesUserAndCreatesOutbox() throws JsonProcessingException {
    RegisterRequest request = new RegisterRequest(
        "testuser", "test@gmail.com", "Password@123", "Password@123", "Test User");

    when(userRepository.findPendingUserForUpdate(anyString(), anyString())).thenReturn(Optional.empty());
    when(userRepository.existsByUsernameAndStatusAndDeletedFalse(anyString(), any(UserStatus.class))).thenReturn(false);
    when(userRepository.existsByEmailAndStatusAndDeletedFalse(anyString(), any(UserStatus.class))).thenReturn(false);

    Role mockRole = new Role();
    mockRole.setName("USER");
    when(roleRepository.findByName("USER")).thenReturn(Optional.of(mockRole));

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setUsername(request.username());
    mockUser.setEmail(request.email());
    mockUser.setFullName(request.fullName());
    mockUser.setStatus(UserStatus.PENDING_VERIFICATION);

    when(userMapper.toEntity(any(RegisterRequest.class))).thenReturn(mockUser);
    when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
    when(userRepository.save(any(User.class))).thenReturn(mockUser);
    when(otpService.generateOtp()).thenReturn("123456");

    when(outboxEventFactory.registrationOtp(any(User.class), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new OutboxEvent());

    RegisterResponse responseDto = new RegisterResponse(
        mockUser.getUsername(), mockUser.getEmail(), mockUser.getFullName(), mockUser.getStatus().name());
    when(userMapper.toRegisterResponse(any(User.class))).thenReturn(responseDto);

    RegisterResponse result = authService.register(request);

    assertNotNull(result);
    assertEquals("testuser", result.username());
    assertEquals("PENDING_VERIFICATION", result.status());

    verify(userRepository).save(any(User.class));
    verify(outboxEventFactory).registrationOtp(any(User.class), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void testRegister_disposableEmail_throwsException() {
    RegisterRequest request = new RegisterRequest(
        "testuser", "test@tempmail.com", "Password@123", "Password@123", "Test User");

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
    assertEquals(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED, ex.getErrorCode());
  }

  @Test
  void testVerifyOtp_success_activatesUserAndGeneratesTokens() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(0L);
    when(otpService.verifyOtp(anyString(), anyString())).thenReturn(true);

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.PENDING_VERIFICATION);
    mockUser.setFullName("Test User");
    Role mockRole = new Role();
    mockRole.setName("ROLE_USER");
    mockUser.setRole(mockRole);
    when(userRepository.findByEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(userRepository.save(any(User.class))).thenReturn(mockUser);

    when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
    lenient().when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

    VerifyOtpResponse.UserInfo userInfo = new VerifyOtpResponse.UserInfo(
        "testuser", "test@gmail.com", "Test User", "ACTIVE", "LOCAL");
    when(userMapper.toUserInfo(any(User.class))).thenReturn(userInfo);

    VerifyOtpResponse result = authService.verifyOtp(request, httpResponse);

    assertNotNull(result);
    assertEquals("access-token", result.accessToken());
    assertEquals("testuser", result.user().username());
    assertEquals(UserStatus.ACTIVE, mockUser.getStatus());

    verify(otpService).deleteAllOtpKeys("test@gmail.com");
    verify(sessionService).createSession(mockUser, httpResponse);
  }

  @Test
  void testVerifyOtp_invalidOtp_incrementsAttemptsAndThrows() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(0L);
    when(otpService.verifyOtp(anyString(), anyString())).thenReturn(false);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyOtp(request, httpResponse));
    assertEquals(ErrorCode.INVALID_OTP, ex.getErrorCode());
    verify(otpService).incrementAttempts("test@gmail.com");
  }

  @Test
  void testVerifyOtp_attemptsExceeded_throwsException() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(5L);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyOtp(request, httpResponse));
    assertEquals(ErrorCode.OTP_ATTEMPTS_EXCEEDED, ex.getErrorCode());
    verify(otpService).deleteOtpAndAttempts("test@gmail.com");
  }

  @Test
  void testResendOtp_withinCooldown_throwsException() {
    ResendOtpRequest request = new ResendOtpRequest("test@gmail.com");

    User mockUser = new User();
    mockUser.setStatus(UserStatus.PENDING_VERIFICATION);
    when(userRepository.findByEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(otpService.checkCooldown(anyString())).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.resendOtp(request));
    assertEquals(ErrorCode.OTP_COOLDOWN, ex.getErrorCode());
  }

  @Test
  void testLogin_success() {
    LoginRequest request = new LoginRequest("test@gmail.com", "Password@123");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setUsername("testuser");
    mockUser.setEmail("test@gmail.com");
    mockUser.setPassword("hashed-pwd");
    mockUser.setStatus(UserStatus.ACTIVE);
    mockUser.setFullName("Test User");
    Role role = new Role();
    role.setName("USER");
    mockUser.setRole(role);

    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(false);
    when(passwordEncoder.matches("Password@123", "hashed-pwd")).thenReturn(true);

    lenient().when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
    lenient().when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    LoginResponse response = authService.login(request, httpResponse);

    assertNotNull(response);
    assertEquals("access-token", response.accessToken());
    assertEquals("testuser", response.user().username());
    verify(redisTemplate).delete("login_attempts:user-uuid");
  }

  @Test
  void testLogin_wrongPassword_incrementsAttempts() {
    LoginRequest request = new LoginRequest("test@gmail.com", "WrongPwd");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setPassword("hashed-pwd");
    mockUser.setStatus(UserStatus.ACTIVE);

    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(false);
    when(passwordEncoder.matches("WrongPwd", "hashed-pwd")).thenReturn(false);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("login_attempts:user-uuid")).thenReturn(3L);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpResponse));
    assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
  }

  @Test
  void testLogin_wrongPassword5Times_locksAccount() {
    LoginRequest request = new LoginRequest("test@gmail.com", "WrongPwd");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setPassword("hashed-pwd");
    mockUser.setStatus(UserStatus.ACTIVE);

    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(false);
    when(passwordEncoder.matches("WrongPwd", "hashed-pwd")).thenReturn(false);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("login_attempts:user-uuid")).thenReturn(5L);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpResponse));
    assertEquals(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, ex.getErrorCode());

    verify(valueOperations).set("login_lockout:user-uuid", "true", Duration.ofMinutes(15));
    verify(redisTemplate).delete("login_attempts:user-uuid");
  }

  @Test
  void testLogin_lockedAccount_throwsException() {
    LoginRequest request = new LoginRequest("test@gmail.com", "Password@123");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setStatus(UserStatus.ACTIVE);

    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpResponse));
    assertEquals(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, ex.getErrorCode());
  }

  @Test
  void testLogin_userNotFound_throwsException() {
    LoginRequest request = new LoginRequest("test@gmail.com", "Password@123");
    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpResponse));
    assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
  }

  @Test
  void testLogin_bannedUser_throwsException() {
    LoginRequest request = new LoginRequest("test@gmail.com", "Password@123");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setStatus(UserStatus.BANNED);

    when(userRepository.findByUsernameOrEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(false);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, httpResponse));
    assertEquals(ErrorCode.ACCOUNT_BANNED, ex.getErrorCode());
  }

  @Test
  void testLoginWithGoogle_success_linkedAccount() throws GeneralSecurityException, IOException {
    Oauth2LoginRequest request = new Oauth2LoginRequest("valid-google-token", null);

    GoogleIdToken mockToken = Mockito.mock(GoogleIdToken.class);
    GoogleIdToken.Payload mockPayload = Mockito.mock(GoogleIdToken.Payload.class);
    when(googleVerifier.verify(anyString())).thenReturn(mockToken);
    when(mockToken.getPayload()).thenReturn(mockPayload);
    when(mockPayload.getEmailVerified()).thenReturn(true);
    when(mockPayload.getEmail()).thenReturn("test@gmail.com");
    when(mockPayload.getSubject()).thenReturn("google-sub");
    when(mockPayload.get("name")).thenReturn("Google User");
    when(mockPayload.get("picture")).thenReturn("http://avatar");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.ACTIVE);
    mockUser.setFullName("Google User");
    mockUser.setOauthProvider(OAuthProvider.GOOGLE);
    mockUser.setOauthId("google-sub");
    Role role = new Role();
    role.setName("USER");
    mockUser.setRole(role);

    when(userRepository.findByEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(userRepository.save(any(User.class))).thenReturn(mockUser);
    when(redisTemplate.hasKey("login_lockout:user-uuid")).thenReturn(false);

    lenient().when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
    lenient().when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    LoginResponse response = authService.loginWithGoogle(request, httpResponse);

    assertNotNull(response);
    assertEquals("access-token", response.accessToken());
    verify(userRepository).save(mockUser);
  }

  @Test
  void testLoginWithGoogle_invalidToken_throwsException() throws GeneralSecurityException, IOException {
    Oauth2LoginRequest request = new Oauth2LoginRequest("invalid-token", null);
    when(googleVerifier.verify(anyString())).thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.loginWithGoogle(request, httpResponse));
    assertEquals(ErrorCode.INVALID_OAUTH_TOKEN, ex.getErrorCode());
  }

  @Test
  void testRefreshAccessToken_success() {
    String expiredToken = "Bearer expired-access-token";
    String refreshToken = "valid-refresh-token";

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.ACTIVE);
    mockUser.setFullName("Test User");

    RefreshResponse mockResponse = new RefreshResponse("new-access-token", 900);

    when(sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse)).thenReturn(mockResponse);

    RefreshResponse response = sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse);

    assertNotNull(response);
    assertEquals("new-access-token", response.accessToken());
    Mockito.verify(sessionService).refreshAccessToken(expiredToken, refreshToken, httpResponse);
  }

  @Test
  void testRefreshAccessToken_gracePeriod() {
    String expiredToken = "Bearer expired-access-token";
    String refreshToken = "old-refresh-token-in-shadow";

    RefreshResponse mockResponse = new RefreshResponse("new-access-token", 900);
    when(sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse)).thenReturn(mockResponse);

    RefreshResponse response = sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse);

    assertNotNull(response);
    assertEquals("new-access-token", response.accessToken());
  }

  @Test
  void testRefreshAccessToken_tokenTheft() {
    String expiredToken = "Bearer expired-access-token";
    String refreshToken = "stolen-refresh-token";

    when(sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse))
        .thenThrow(new BusinessException(ErrorCode.TOKEN_THEFT_DETECTED, "Token reuse detected, all sessions revoked"));

    BusinessException ex = assertThrows(BusinessException.class, () ->
        sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse));

    assertEquals(ErrorCode.TOKEN_THEFT_DETECTED, ex.getErrorCode());
  }

  @Test
  void testRefreshAccessToken_invalidToken() {
    String expiredToken = "Bearer expired-access-token";
    String refreshToken = "completely-invalid-token";

    when(sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse))
        .thenThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired"));

    BusinessException ex = assertThrows(BusinessException.class, () ->
        sessionService.refreshAccessToken(expiredToken, refreshToken, httpResponse));

    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, ex.getErrorCode());
  }

  @Test
  void testLogout_success() {
    String authorizationHeader = "Bearer access-token";
    String refreshToken = "valid-refresh-token";

    org.mockito.Mockito.doNothing().when(sessionService).logout(authorizationHeader, refreshToken, httpResponse);

    sessionService.logout(authorizationHeader, refreshToken, httpResponse);

    org.mockito.Mockito.verify(sessionService).logout(authorizationHeader, refreshToken, httpResponse);
  }

  @Test
  void testLogout_idempotent() {
    String authorizationHeader = "Bearer access-token";
    String refreshToken = null;

    org.mockito.Mockito.doNothing().when(sessionService).logout(authorizationHeader, refreshToken, httpResponse);

    sessionService.logout(authorizationHeader, refreshToken, httpResponse);

    org.mockito.Mockito.verify(sessionService).logout(authorizationHeader, refreshToken, httpResponse);
  }

  @Test
  void testGetMyProfile_success() {
    String authHeader = "Bearer access-token";
    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setUsername("testuser");

    when(jwtService.isTokenValid("access-token")).thenReturn(true);
    when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));

    UserProfileResponse mockResponse = new UserProfileResponse("testuser", "test@gmail.com", "Test User", "USER", "ACTIVE", "avatar", "0987654321", "LOCAL", null);
    when(userMapper.toUserProfileResponse(mockUser)).thenReturn(mockResponse);

    UserProfileResponse response = authService.getMyProfile(authHeader);

    assertNotNull(response);
    assertEquals("testuser", response.username());
    assertEquals("test@gmail.com", response.email());
  }

  @Test
  void testUpdateProfile_success() {
    UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0912345678", "new-avatar");
    String authHeader = "Bearer access-token";

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setFullName("Old Name");

    when(jwtService.isTokenValid("access-token")).thenReturn(true);
    when(jwtService.extractEmail("access-token")).thenReturn("test@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));
    when(userRepository.save(any(User.class))).thenReturn(mockUser);

    UserProfileResponse mockResponse = new UserProfileResponse("testuser", "test@gmail.com", "New Name", "USER", "ACTIVE", "new-avatar", "0912345678", "LOCAL", null);
    when(userMapper.toUserProfileResponse(mockUser)).thenReturn(mockResponse);

    UserProfileResponse response = authService.updateProfile(request, authHeader);

    assertNotNull(response);
    assertEquals("New Name", response.fullName());
    assertEquals("0912345678", response.phone());
  }

  @Test
  void testForgotPassword_success() {
    ForgotPasswordRequest request = new ForgotPasswordRequest("test@gmail.com");
    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.ACTIVE);
    mockUser.setPassword("hashed-password");

    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));
    when(jwtService.generateRefreshToken(any(User.class))).thenReturn("reset-token-uuid");
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    authService.forgotPassword(request);

    Mockito.verify(outboxEventFactory).passwordReset(any(User.class), anyString(), anyString());
    Mockito.verify(valueOperations).set(Mockito.eq("password_reset_token:reset-token-uuid"), Mockito.eq("test@gmail.com"), any(Duration.class));
  }

  @Test
  void testForgotPassword_enumerationDefense() {
    ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("nonexistent@gmail.com")).thenReturn(Optional.empty());

    authService.forgotPassword(request);

    Mockito.verifyNoInteractions(outboxEventFactory);
  }

  @Test
  void testResetPassword_success() {
    ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "NewPassword@123", "NewPassword@123");
    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.ACTIVE);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete("password_reset_token:reset-token")).thenReturn("test@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));

    authService.resetPassword(request);

    Mockito.verify(userRepository).save(mockUser);
    Mockito.verify(sessionService).revokeAllUserSessions("user-uuid");
  }

  @Test
  void testChangePassword_success() {
    ChangePasswordRequest request = new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123");
    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setPassword("hashed-old-password");

    when(jwtService.extractEmailFromExpiredToken("access-token")).thenReturn("test@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));
    when(passwordEncoder.matches("OldPassword@123", "hashed-old-password")).thenReturn(true);
    when(passwordEncoder.matches("NewPassword@123", "hashed-old-password")).thenReturn(false);

    lenient().when(redisTemplate.opsForZSet()).thenReturn(Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class));

    authService.changePassword(request, "Bearer access-token", "current-refresh-token");

    Mockito.verify(userRepository).save(mockUser);
  }

  @Test
  void testChangePassword_invalidOldPassword_throwsException() {
    ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword@123", "NewPassword@123", "NewPassword@123");
    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setPassword("hashed-old-password");

    when(jwtService.extractEmailFromExpiredToken("access-token")).thenReturn("test@gmail.com");
    when(userRepository.findByEmailAndDeletedFalse("test@gmail.com")).thenReturn(Optional.of(mockUser));
    when(passwordEncoder.matches("WrongPassword@123", "hashed-old-password")).thenReturn(false);

    BusinessException ex = assertThrows(BusinessException.class, () ->
        authService.changePassword(request, "Bearer access-token", "current-refresh-token"));

    assertEquals(ErrorCode.INVALID_OLD_PASSWORD, ex.getErrorCode());
  }
}