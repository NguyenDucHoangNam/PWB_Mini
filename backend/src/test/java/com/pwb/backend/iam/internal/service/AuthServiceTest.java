package com.pwb.backend.iam.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.internal.config.IamProperties;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
  private OutboxEventRepository outboxEventRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private OtpService otpService;
  @Mock
  private JwtService jwtService;
  @Mock
  private DisposableEmailCheckerService disposableEmailChecker;
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
  private HttpServletResponse httpResponse;
  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RLock rLock;
  @Mock
  private TransactionTemplate transactionTemplate;

  private IamProperties iamProperties;

  @BeforeEach
  void setUp() {
    iamProperties = new IamProperties();
    iamProperties.getOtp().setExpiration(300); // 5 mins
    iamProperties.getOtp().setCooldown(60); // 60s
    iamProperties.getOtp().setMaxAttempts(5);
    iamProperties.getJwt().setAccessTokenExpiration(900); // 15 mins
    iamProperties.getJwt().setRefreshTokenExpiration(604800); // 7 days

    lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    try {
      lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    } catch (InterruptedException e) {
      // ignore
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

    authService = new AuthService(
        userRepository,
        roleRepository,
        outboxEventRepository,
        passwordEncoder,
        otpService,
        jwtService,
        disposableEmailChecker,
        eventPublisher,
        objectMapper,
        iamProperties,
        redisTemplate,
        userMapper,
        redissonClient,
        transactionTemplate
    );
  }

  @Test
  void testRegister_success_savesUserAndCreatesOutbox() throws JsonProcessingException {
    RegisterRequest request = new RegisterRequest(
        "testuser", "test@gmail.com", "Password@123", "Password@123", "Test User");

    when(disposableEmailChecker.isDisposable(anyString())).thenReturn(false);
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

    when(objectMapper.writeValueAsString(any())).thenReturn("{\\\"email\\\":\\\"test@gmail.com\\\"}");
    when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

    RegisterResponse responseDto = new RegisterResponse(
        mockUser.getUsername(), mockUser.getEmail(), mockUser.getFullName(), mockUser.getStatus().name());
    when(userMapper.toRegisterResponse(any(User.class))).thenReturn(responseDto);

    RegisterResponse result = authService.register(request);

    assertNotNull(result);
    assertEquals("testuser", result.username());
    assertEquals("PENDING_VERIFICATION", result.status());

    verify(userRepository).save(any(User.class));
    verify(outboxEventRepository).save(any(OutboxEvent.class));
    verify(eventPublisher).publishEvent(any(Object.class));
  }

  @Test
  void testRegister_disposableEmail_throwsException() {
    RegisterRequest request = new RegisterRequest(
        "testuser", "test@tempmail.com", "Password@123", "Password@123", "Test User");
    when(disposableEmailChecker.isDisposable(anyString())).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
    assertEquals(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED, ex.getErrorCode());
  }

  @Test
  void testVerifyOtp_success_activatesUserAndGeneratesTokens() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(0L);
    when(otpService.getStoredOtp(anyString())).thenReturn("123456");

    User mockUser = new User();
    mockUser.setId("user-uuid");
    mockUser.setEmail("test@gmail.com");
    mockUser.setStatus(UserStatus.PENDING_VERIFICATION);
    when(userRepository.findByEmailAndDeletedFalse(anyString())).thenReturn(Optional.of(mockUser));
    when(userRepository.save(any(User.class))).thenReturn(mockUser);

    when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
    when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

    VerifyOtpResponse.UserInfo userInfo = new VerifyOtpResponse.UserInfo(
        "testuser", "test@gmail.com", "Test User", "ACTIVE");
    when(userMapper.toUserInfo(any(User.class))).thenReturn(userInfo);

    VerifyOtpResponse result = authService.verifyOtp(request, httpResponse);

    assertNotNull(result);
    assertEquals("access-token", result.accessToken());
    assertEquals("testuser", result.user().username());
    assertEquals(UserStatus.ACTIVE, mockUser.getStatus());

    verify(otpService).deleteAllOtpKeys("test@gmail.com");
    verify(httpResponse).addCookie(any(Cookie.class));
  }

  @Test
  void testVerifyOtp_invalidOtp_incrementsAttemptsAndThrows() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(0L);
    when(otpService.getStoredOtp(anyString())).thenReturn("654321"); // KhÃ¡c mÃ£ client gá»­i

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyOtp(request, httpResponse));
    assertEquals(ErrorCode.INVALID_OTP, ex.getErrorCode());
    verify(otpService).incrementAttempts("test@gmail.com");
  }

  @Test
  void testVerifyOtp_attemptsExceeded_throwsException() {
    VerifyOtpRequest request = new VerifyOtpRequest("test@gmail.com", "123456");

    when(otpService.getAttempts(anyString())).thenReturn(5L); // Báº±ng max_attempts = 5

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
    when(otpService.checkCooldown(anyString())).thenReturn(true); // Cooldown Ä‘ang kÃ­ch hoáº¡t

    BusinessException ex = assertThrows(BusinessException.class, () -> authService.resendOtp(request));
    assertEquals(ErrorCode.OTP_COOLDOWN, ex.getErrorCode());
  }
}
