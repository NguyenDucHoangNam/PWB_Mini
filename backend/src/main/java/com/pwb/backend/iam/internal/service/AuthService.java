package com.pwb.backend.iam.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.mapper.UserMapper;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.iam.internal.repository.RoleRepository;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ROLE_USER = "USER";
  private static final String AGGREGATE_TYPE_IAM = "IAM";
  private static final String EVENT_TYPE_REGISTRATION_OTP = "REGISTRATION_OTP";
  private static final String SESSION_KEY_PREFIX = "session:refresh_token:";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final PasswordEncoder passwordEncoder;
  private final OtpService otpService;
  private final JwtService jwtService;
  private final DisposableEmailChecker disposableEmailChecker;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final IamProperties iamProperties;
  private final StringRedisTemplate redisTemplate;
  private final UserMapper userMapper;

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    if (disposableEmailChecker.isDisposable(normalizedEmail)) {
      throw new BusinessException(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED,
          "Disposable email addresses are not allowed");
    }

    Optional<User> existingPending = userRepository.findPendingUserForUpdate(
        request.username(), normalizedEmail);

    if (existingPending.isPresent()) {
      User pendingUser = existingPending.get();
      long otpExpirationSeconds = iamProperties.getOtp().getExpiration();
      Instant cutoff = Instant.now().minus(otpExpirationSeconds, ChronoUnit.SECONDS);

      if (pendingUser.getCreatedAt().isAfter(cutoff)) {
        throw new BusinessException(ErrorCode.REGISTRATION_IN_PROGRESS,
            "Registration is already in progress for this account");
      }

      otpService.deleteAllOtpKeys(pendingUser.getEmail());
      updatePendingUser(pendingUser, request, normalizedEmail);
      String otpCode = otpService.generateOtp();
      createOutboxEvent(pendingUser, normalizedEmail, otpCode, pendingUser.getFullName());

      return userMapper.toRegisterResponse(pendingUser);
    }

    if (userRepository.existsByUsernameAndStatusAndDeletedFalse(
        request.username(), UserStatus.ACTIVE)) {
      throw new BusinessException(ErrorCode.USERNAME_EXISTED,
          "Username is already taken");
    }
    if (userRepository.existsByEmailAndStatusAndDeletedFalse(
        normalizedEmail, UserStatus.ACTIVE)) {
      throw new BusinessException(ErrorCode.EMAIL_EXISTED,
          "Email is already in use");
    }

    String otpCode = otpService.generateOtp();

    try {
      User newUser = createNewUser(request, normalizedEmail);
      createOutboxEvent(newUser, normalizedEmail, otpCode, newUser.getFullName());

      return userMapper.toRegisterResponse(newUser);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.REGISTRATION_IN_PROGRESS,
          "Registration is already in progress for this account");
    }
  }

  public CheckUsernameResponse checkUsernameAvailability(String username) {
    boolean exists = userRepository.existsByUsernameAndStatusAndDeletedFalse(
        username, UserStatus.ACTIVE);
    return new CheckUsernameResponse(username, !exists);
  }

  @Transactional
  public VerifyOtpResponse verifyOtp(VerifyOtpRequest request,
      HttpServletResponse httpResponse) {
    String normalizedEmail = request.email().trim().toLowerCase();

    long attempts = otpService.getAttempts(normalizedEmail);
    if (attempts >= iamProperties.getOtp().getMaxAttempts()) {
      otpService.deleteOtpAndAttempts(normalizedEmail);
      throw new BusinessException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
          "Too many failed OTP attempts, please request a new code");
    }

    String storedOtp = otpService.getStoredOtp(normalizedEmail);
    if (storedOtp == null) {
      throw new BusinessException(ErrorCode.OTP_EXPIRED,
          "OTP has expired, please request a new one");
    }

    if (!storedOtp.equals(request.otpCode())) {
      otpService.incrementAttempts(normalizedEmail);
      throw new BusinessException(ErrorCode.INVALID_OTP, "Invalid OTP code");
    }

    otpService.deleteAllOtpKeys(normalizedEmail);

    User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED,
            "User does not exist"));

    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);

    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken();

    redisTemplate.opsForValue().set(
        SESSION_KEY_PREFIX + refreshToken,
        user.getEmail(),
        Duration.ofSeconds(iamProperties.getJwt().getRefreshTokenExpiration()));

    Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
    refreshCookie.setHttpOnly(true);
    refreshCookie.setSecure(true);
    refreshCookie.setPath("/");
    refreshCookie.setMaxAge(
        (int) iamProperties.getJwt().getRefreshTokenExpiration());
    refreshCookie.setAttribute("SameSite", "Strict");
    httpResponse.addCookie(refreshCookie);

    return new VerifyOtpResponse(
        accessToken,
        iamProperties.getJwt().getAccessTokenExpiration(),
        userMapper.toUserInfo(user));
  }

  @Transactional
  public void resendOtp(ResendOtpRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED,
            "User does not exist"));

    if (user.getStatus() == UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVE,
          "Account has already been activated");
    }

    if (otpService.checkCooldown(normalizedEmail)) {
      throw new BusinessException(ErrorCode.OTP_COOLDOWN,
          "Please wait before requesting a new OTP");
    }

    String otpCode = otpService.generateOtp();
    createOutboxEvent(user, normalizedEmail, otpCode, user.getFullName());
  }

  private User createNewUser(RegisterRequest request, String normalizedEmail) {
    Role defaultRole = roleRepository.findByName(ROLE_USER)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
            "Default role USER not found"));

    User user = userMapper.toEntity(request);
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setRole(defaultRole);

    return userRepository.save(user);
  }

  private void updatePendingUser(User user, RegisterRequest request, String normalizedEmail) {
    user.setUsername(request.username());
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setFullName(request.fullName());
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());

    userRepository.save(user);
  }

  private void createOutboxEvent(User user, String email, String otpCode, String fullName) {
    try {
      String payload = objectMapper.writeValueAsString(Map.of(
          "email", email,
          "otpCode", otpCode,
          "fullName", fullName
      ));

      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
      outboxEvent.setAggregateId(user.getId());
      outboxEvent.setEventType(EVENT_TYPE_REGISTRATION_OTP);
      outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
      outboxEvent.setPayload(payload);
      outboxEvent.setStatus(OutboxEventStatus.PENDING);

      OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
      eventPublisher.publishEvent(new OutboxCreatedEvent(savedEvent.getId()));
    } catch (JsonProcessingException ex) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to serialize outbox event payload");
    }
  }
}
