package com.pwb.backend.iam.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pwb.backend.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.backend.iam.api.dto.request.DeleteAccountRequest;
import com.pwb.backend.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.ActiveSessionResponse;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.RefreshResponse;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OAuthProvider;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
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
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ROLE_USER = "USER";
  private static final String AGGREGATE_TYPE_IAM = "IAM";
  private static final String EVENT_TYPE_REGISTRATION_OTP = "REGISTRATION_OTP";
  private static final String EVENT_TYPE_WELCOME_EMAIL = "WELCOME_EMAIL";
  private static final String SESSION_KEY_PREFIX = "session:refresh_token:";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final PasswordEncoder passwordEncoder;
  private final OtpService otpService;
  private final JwtService jwtService;
  private final DisposableEmailCheckerService disposableEmailChecker;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final IamProperties iamProperties;
  private final StringRedisTemplate redisTemplate;
  private final UserMapper userMapper;
  private final RedissonClient redissonClient;
  private final TransactionTemplate transactionTemplate;
  private final RedisScript<List<String>> concurrentSessionScript;
  private final RedisScript<String> sessionRotationScript;
  private final GeoIpService geoIpService;
  private final RedisScript<List<String>> revokeOtherSessionsScript;
  private final PlatformTransactionManager transactionManager;
  private TransactionTemplate requiresNewTemplate;


  private GoogleIdTokenVerifier googleVerifier;

  @PostConstruct
  public void init() {
    this.requiresNewTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    if (iamProperties.getGoogle() != null && iamProperties.getGoogle().getClientId() != null) {
      NetHttpTransport transport = new NetHttpTransport();
      GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
      googleVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
          .setAudience(Collections.singletonList(iamProperties.getGoogle().getClientId()))
          .build();
    }
  }

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
      otpService.storeOtpWithAttemptsReset(normalizedEmail, otpCode);
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
      otpService.storeOtp(normalizedEmail, otpCode);
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

  public VerifyOtpResponse verifyOtp(VerifyOtpRequest request,
      HttpServletResponse httpResponse) {
    String normalizedEmail = request.email().trim().toLowerCase();
    String lockKey = "lock:otp:verify:" + normalizedEmail;
    RLock lock = redissonClient.getLock(lockKey);

    try {
      if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
            "Request is already being processed");
      }
      return transactionTemplate.execute(status ->
          executeVerifyOtp(normalizedEmail, request, httpResponse));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Authentication process interrupted");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  @Transactional
  protected VerifyOtpResponse executeVerifyOtp(String normalizedEmail,
      VerifyOtpRequest request, HttpServletResponse httpResponse) {
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

    createWelcomeEmailOutbox(user);

    return generateVerifyOtpResponse(user, httpResponse);
  }

  public void resendOtp(ResendOtpRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    String lockKey = "lock:otp:resend:" + normalizedEmail;
    RLock lock = redissonClient.getLock(lockKey);

    try {
      if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
            "Request is already being processed");
      }
      transactionTemplate.executeWithoutResult(status ->
          executeResendOtp(normalizedEmail));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Process interrupted");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  @Transactional
  protected void executeResendOtp(String normalizedEmail) {
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
    otpService.storeOtpWithAttemptsReset(normalizedEmail, otpCode);
    createOutboxEvent(user, normalizedEmail, otpCode, user.getFullName());
  }

  public LoginResponse login(LoginRequest request, HttpServletResponse httpResponse) {
    User user = userRepository.findByUsernameOrEmailAndDeletedFalse(request.usernameOrEmail())
        .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS,
            "Incorrect username or password"));

    String userId = user.getId();
    String lockoutKey = "login_lockout:" + userId;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
      throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
          "Account is temporarily locked, please try again later");
    }

    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
      throw new BusinessException(ErrorCode.REGISTRATION_IN_PROGRESS,
          "Please verify your account");
    }

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      String attemptsKey = "login_attempts:" + userId;
      Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
      if (attempts != null && attempts == 1) {
        redisTemplate.expire(attemptsKey, Duration.ofMinutes(15));
      }
      if (attempts != null && attempts >= 5) {
        redisTemplate.opsForValue().set(lockoutKey, "true", Duration.ofMinutes(15));
        redisTemplate.delete(attemptsKey);
        throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
            "Account is temporarily locked, please try again later");
      }
      throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Incorrect username or password");
    }

    redisTemplate.delete("login_attempts:" + userId);

    return generateSessionAndResponse(user, httpResponse);
  }

  public LoginResponse loginWithGoogle(Oauth2LoginRequest request, HttpServletResponse httpResponse) {
    if (googleVerifier == null) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Google Verifier is not initialized");
    }

    GoogleIdToken idToken;
    try {
      idToken = googleVerifier.verify(request.idToken());
    } catch (Exception e) {
      log.error("Google token verification failed", e);
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Invalid OAuth token, please try again");
    }

    if (idToken == null) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Invalid OAuth token, please try again");
    }

    GoogleIdToken.Payload payload = idToken.getPayload();
    if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Google email is not verified");
    }

    String email = payload.getEmail().trim().toLowerCase();
    String sub = payload.getSubject();
    String name = (String) payload.get("name");
    String picture = (String) payload.get("picture");

    User user = handleOauthAccountLinker(email, sub, name, picture);

    String userId = user.getId();
    String lockoutKey = "login_lockout:" + userId;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
      throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
          "Account is temporarily locked, please try again later");
    }

    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    return generateSessionAndResponse(user, httpResponse);
  }

  public RefreshResponse refreshAccessToken(String expiredAccessTokenHeader, String refreshToken, HttpServletResponse response) {
    if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String expiredToken = expiredAccessTokenHeader.substring(7);

    String email;
    try {
      email = jwtService.extractEmailFromExpiredToken(expiredToken);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    String userId = user.getId();
    String lockoutKey = "login_lockout:" + userId;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
      throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
          "Account is temporarily locked, please try again later");
    }

    String activeKey = "session:refresh_token:" + refreshToken;
    String shadowKey = "session:refresh_token:shadow:" + refreshToken;
    String revokedKey = "session:refresh_token:revoked:" + refreshToken;

    String cachedUserId = redisTemplate.opsForValue().get(activeKey);

    if (cachedUserId != null) {
      if (!cachedUserId.equals(userId)) {
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token does not match user");
      }

      String newAccessToken = jwtService.generateAccessToken(user);
      String newRefreshToken = jwtService.generateRefreshToken();

      String newActiveKey = "session:refresh_token:" + newRefreshToken;
      String zsetKey = "user:sessions:" + userId;

      long currentTimestamp = Instant.now().getEpochSecond();
      long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();

      List<String> keys = List.of(activeKey, shadowKey, revokedKey, newActiveKey, zsetKey);
      Object[] args = new Object[]{
          userId,
          newRefreshToken,
          refreshToken,
          "10",
          String.valueOf(refreshTokenExpiry),
          String.valueOf(refreshTokenExpiry),
          String.valueOf(currentTimestamp)
      };

      redisTemplate.execute(sessionRotationScript, keys, args);

      String oldMetadataKey = "session:metadata:" + refreshToken;
      String newMetadataKey = "session:metadata:" + newRefreshToken;
      if (Boolean.TRUE.equals(redisTemplate.hasKey(oldMetadataKey))) {
        redisTemplate.rename(oldMetadataKey, newMetadataKey);
        redisTemplate.opsForHash().put(newMetadataKey, "active_jwt_signature", jwtService.getSignature(newAccessToken));
        redisTemplate.expire(newMetadataKey, refreshTokenExpiry, TimeUnit.SECONDS);
      } else {
        String ipAddress = getClientIp();
        String userAgent = getUserAgent();
        String location = geoIpService.getLocation(ipAddress);
        String browser = userAgent;
        String os = "Unknown";
        if (userAgent != null) {
          if (userAgent.contains("Windows")) os = "Windows";
          else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) os = "macOS";
          else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) os = "iOS";
          else if (userAgent.contains("Android")) os = "Android";
          else if (userAgent.contains("Linux")) os = "Linux";
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("ip", ipAddress);
        metadata.put("browser", browser);
        metadata.put("os", os);
        metadata.put("location", location);
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("active_jwt_signature", jwtService.getSignature(newAccessToken));
        redisTemplate.opsForHash().putAll(newMetadataKey, metadata);
        redisTemplate.expire(newMetadataKey, refreshTokenExpiry, TimeUnit.SECONDS);
      }

      setRefreshCookie(response, newRefreshToken, (int) refreshTokenExpiry);

      return new RefreshResponse(newAccessToken, iamProperties.getJwt().getAccessTokenExpiration());
    }

    String newToken = redisTemplate.opsForValue().get(shadowKey);
    if (newToken != null) {
      String shadowUser = redisTemplate.opsForValue().get("session:refresh_token:" + newToken);
      if (shadowUser == null || !shadowUser.equals(userId)) {
        throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid session context");
      }

      String newAccessToken = jwtService.generateAccessToken(user);

      long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();
      setRefreshCookie(response, newToken, (int) refreshTokenExpiry);

      return new RefreshResponse(newAccessToken, iamProperties.getJwt().getAccessTokenExpiration());
    }

    String revokedUserId = redisTemplate.opsForValue().get(revokedKey);
    if (revokedUserId != null) {
      log.warn("Token Theft detected for user {} using revoked token {}", revokedUserId, refreshToken);

      revokeAllUserSessions(revokedUserId);
      throw new BusinessException(ErrorCode.TOKEN_THEFT_DETECTED, "Token reuse detected, all sessions revoked");
    }

    throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired");
  }

  public void logout(String expiredAccessTokenHeader, String refreshToken, HttpServletResponse response) {
    if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String expiredToken = expiredAccessTokenHeader.substring(7);

    String email;
    Claims claims;
    try {
      claims = jwtService.extractClaimsFromExpiredToken(expiredToken);
      email = claims.getSubject();
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String jwtSignature = jwtService.getSignature(expiredToken);

    long blacklistTtl = calculateBlacklistTtl(claims);

    String blacklistKey = "session:blacklist_token:" + jwtSignature;
    String activeKey = "session:refresh_token:" + refreshToken;
    String zsetKey = "user:sessions:" + userId;

    if (blacklistTtl > 0) {
      redisTemplate.opsForValue().set(blacklistKey, "true", Duration.ofSeconds(blacklistTtl));
    }

    if (refreshToken != null && !refreshToken.isEmpty()) {
      redisTemplate.delete(activeKey);
      redisTemplate.delete("session:metadata:" + refreshToken);
      redisTemplate.opsForZSet().remove(zsetKey, refreshToken);
    }

    clearRefreshCookie(response);
  }

  public void forgotPassword(ForgotPasswordRequest request) {
    long startTime = System.currentTimeMillis();

    Optional<User> userOpt = userRepository.findByEmailAndDeletedFalse(request.email());

    if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.ACTIVE || userOpt.get().getPassword() == null) {
      long duration = System.currentTimeMillis() - startTime;
      long targetDuration = 500;
      if (duration < targetDuration) {
        try {
          long delay = (targetDuration - duration) + (long) (Math.random() * 50);
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return;
    }

    User user = userOpt.get();
    String token = jwtService.generateRefreshToken();

    transactionTemplate.executeWithoutResult(status -> {
      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
      outboxEvent.setAggregateId(user.getId());
      outboxEvent.setEventType("PASSWORD_RESET");

      Map<String, Object> payload = new HashMap<>();
      payload.put("email", user.getEmail());
      payload.put("fullName", user.getFullName());
      payload.put("token", token);
      payload.put("locale", LocaleContextHolder.getLocale().getLanguage());

      try {
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
      } catch (JsonProcessingException e) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize outbox event payload");
      }

      outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
      outboxEvent.setStatus(OutboxEventStatus.PENDING);
      OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
      eventPublisher.publishEvent(new OutboxCreatedEvent(savedEvent.getId()));
    });

    redisTemplate.opsForValue().set("password_reset_token:" + token, user.getEmail(), Duration.ofMinutes(10));
  }

  public void resetPassword(ResetPasswordRequest request) {
    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Passwords do not match");
    }

    String tokenKey = "password_reset_token:" + request.token();
    String email = redisTemplate.opsForValue().getAndDelete(tokenKey);
    if (email == null) {
      throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN, "Reset token is invalid or expired");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    transactionTemplate.executeWithoutResult(status -> {
      user.setPassword(passwordEncoder.encode(request.newPassword()));
      userRepository.save(user);
    });

    String userId = user.getId();
    revokeAllUserSessions(userId);

    redisTemplate.delete("login_lockout:" + userId);
    redisTemplate.delete("login_attempts:" + userId);
  }

  public void changePassword(ChangePasswordRequest request, String expiredAccessTokenHeader, String currentRefreshToken) {
    if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String expiredToken = expiredAccessTokenHeader.substring(7);

    String email;
    try {
      email = jwtService.extractEmailFromExpiredToken(expiredToken);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getPassword() == null) {
      throw new BusinessException(ErrorCode.OAUTH_ONLY_ACCOUNT, "Cannot change password for OAuth-only account");
    }

    if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD, "Incorrect old password");
    }

    if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.PASSWORD_REUSE_BLOCKED, "New password must be different from the old password");
    }

    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Passwords do not match");
    }

    transactionTemplate.executeWithoutResult(status -> {
      user.setPassword(passwordEncoder.encode(request.newPassword()));
      userRepository.save(user);
    });

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;
    Set<String> sessionTokens = redisTemplate.opsForZSet().range(zsetKey, 0, -1);
    if (sessionTokens != null && !sessionTokens.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      for (String t : sessionTokens) {
        if (!t.equals(currentRefreshToken)) {
          blacklistSessionJwt(t, blacklistTtl);
          redisTemplate.delete("session:refresh_token:" + t);
          redisTemplate.delete("session:metadata:" + t);
          redisTemplate.opsForZSet().remove(zsetKey, t);
        }
      }
    }

    redisTemplate.delete("login_lockout:" + userId);
    redisTemplate.delete("login_attempts:" + userId);
  }

  public UserProfileResponse getMyProfile(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));
    return userMapper.toUserProfileResponse(user);
  }

  public UserProfileResponse updateProfile(UpdateProfileRequest request, String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    user.setFullName(request.fullName());
    if (request.phone() != null) {
      user.setPhone(request.phone());
    }
    if (request.avatarUrl() != null) {
      user.setAvatarUrl(request.avatarUrl());
    }

    User savedUser = userRepository.save(user);
    return userMapper.toUserProfileResponse(savedUser);
  }

  public void deleteAccount(DeleteAccountRequest request, String expiredAccessTokenHeader, String currentRefreshToken, HttpServletResponse response) {
    if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String expiredToken = expiredAccessTokenHeader.substring(7);

    String email;
    try {
      email = jwtService.extractEmailFromExpiredToken(expiredToken);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid access token signature or format");
    }

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() == UserStatus.PENDING_DELETION) {
      throw new BusinessException(ErrorCode.DELETION_ALREADY_REQUESTED, "Account deletion has already been requested");
    }

    String userId = user.getId();
    String lockoutKey = "login_lockout:" + userId;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
      throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, "Account is temporarily locked, please try again later");
    }

    if (user.getPassword() != null) {
      if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
        String attemptsKey = "login_attempts:" + userId;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
          redisTemplate.expire(attemptsKey, Duration.ofMinutes(15));
        }
        if (attempts != null && attempts >= 5) {
          redisTemplate.opsForValue().set(lockoutKey, "true", Duration.ofMinutes(15));
          redisTemplate.delete(attemptsKey);
          throw new BusinessException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED, "Account is temporarily locked, please try again later");
        }
        throw new BusinessException(ErrorCode.INVALID_PASSWORD, "Incorrect password confirmation");
      }
      redisTemplate.delete("login_attempts:" + userId);
    } else {
      if (request.idToken() == null) {
        throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Google idToken is required");
      }
      try {
        GoogleIdToken googleIdToken = googleVerifier.verify(request.idToken());
        if (googleIdToken == null) {
          throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Invalid Google idToken");
        }
      } catch (Exception e) {
        throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN, "Invalid Google idToken");
      }
    }

    user.setStatus(UserStatus.PENDING_DELETION);
    user.setDeletionRequestedAt(Instant.now());

    transactionTemplate.executeWithoutResult(status -> {
      userRepository.save(user);

      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
      outboxEvent.setAggregateId(user.getId());
      outboxEvent.setEventType("ACCOUNT_DELETION_REQUESTED");

      Map<String, Object> payload = new HashMap<>();
      payload.put("email", user.getEmail());
      payload.put("fullName", user.getFullName());

      DateTimeFormatter formatter = DateTimeFormatter
          .ofPattern("yyyy-MM-dd HH:mm:ss")
          .withZone(ZoneId.systemDefault());
      String deletionDate = formatter.format(Instant.now().plus(30, ChronoUnit.DAYS));
      payload.put("deletionDate", deletionDate);
      payload.put("locale", LocaleContextHolder.getLocale().getLanguage());

      try {
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
      } catch (JsonProcessingException e) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize outbox event payload");
      }

      outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
      outboxEvent.setStatus(OutboxEventStatus.PENDING);
      OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
      eventPublisher.publishEvent(new OutboxCreatedEvent(savedEvent.getId()));
    });

    revokeAllUserSessions(userId);
    redisTemplate.delete("user:last_login:" + userId);

    clearRefreshCookie(response);
  }

  public List<ActiveSessionResponse> getActiveSessions(String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;
    Set<String> sessionTokens = redisTemplate.opsForZSet().range(zsetKey, 0, -1);

    List<ActiveSessionResponse> responseList = new ArrayList<>();
    if (sessionTokens != null) {
      for (String t : sessionTokens) {
        String metadataKey = "session:metadata:" + t;
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metadataKey);

        String ipAddress = (String) metadata.getOrDefault("ip", "Unknown");
        String browser = (String) metadata.getOrDefault("browser", "Unknown");
        String os = (String) metadata.getOrDefault("os", "Unknown");
        String location = (String) metadata.getOrDefault("location", "Unknown");
        String createdAtStr = (String) metadata.get("createdAt");
        Instant createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();

        String deviceInfo = browser;
        if (!"Unknown".equals(os)) {
          deviceInfo = browser + " (" + os + ")";
        }

        boolean isCurrent = t.equals(currentRefreshToken);

        responseList.add(new ActiveSessionResponse(t, ipAddress, deviceInfo, location, createdAt, isCurrent));
      }
    }
    return responseList;
  }

  public void revokeSession(String tokenUuid, String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;

    Double score = redisTemplate.opsForZSet().score(zsetKey, tokenUuid);
    if (score == null) {
      throw new BusinessException(ErrorCode.SESSION_NOT_FOUND, "Session not found or does not belong to this user");
    }

    if (tokenUuid.equals(currentRefreshToken)) {
      throw new BusinessException(ErrorCode.CANNOT_REVOKE_CURRENT_SESSION, "Cannot revoke current session");
    }

    String metadataKey = "session:metadata:" + tokenUuid;
    String signature = (String) redisTemplate.opsForHash().get(metadataKey, "active_jwt_signature");

    redisTemplate.delete("session:refresh_token:" + tokenUuid);
    redisTemplate.delete(metadataKey);
    redisTemplate.opsForZSet().remove(zsetKey, tokenUuid);

    if (signature != null && !signature.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      redisTemplate.opsForValue().set("session:blacklist_token:" + signature, "true", Duration.ofSeconds(blacklistTtl));
    }
  }

  public void revokeOtherSessions(String authHeader, String currentRefreshToken) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    String userId = user.getId();
    String zsetKey = "user:sessions:" + userId;

    List<String> keys = List.of(zsetKey);
    Object[] args = new Object[]{currentRefreshToken != null ? currentRefreshToken : ""};

    List<String> blacklistedSignatures = redisTemplate.execute(revokeOtherSessionsScript, keys, args);

    if (blacklistedSignatures != null && !blacklistedSignatures.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;

      redisTemplate.executePipelined(new BlacklistSessionCallbackService(blacklistedSignatures, blacklistTtl));
    }
  }

  private static class BlacklistSessionCallbackService implements SessionCallback<Object> {
    private final List<String> blacklistedSignatures;
    private final long finalRemainingTtl;

    public BlacklistSessionCallbackService(List<String> blacklistedSignatures, long finalRemainingTtl) {
      this.blacklistedSignatures = blacklistedSignatures;
      this.finalRemainingTtl = finalRemainingTtl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Object execute(org.springframework.data.redis.core.RedisOperations<K, V> operations) {
      for (String signature : blacklistedSignatures) {
        operations.opsForValue().set((K) ("session:blacklist_token:" + signature), (V) "true", Duration.ofSeconds(finalRemainingTtl));
      }
      return null;
    }
  }

  public TriggerAnonymizationResponse triggerAnonymization() {
    long startTime = System.currentTimeMillis();
    Instant cutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
    List<User> usersToAnonymize = userRepository.findUsersPendingDeletionBefore(cutoff, org.springframework.data.domain.PageRequest.of(0, 100));

    int count = 0;
    for (User user : usersToAnonymize) {
      try {
        requiresNewTemplate.executeWithoutResult(status -> anonymizeUser(user));
        count++;
        log.info("USER_ANONYMIZED_SUCCESS: userId={}", user.getId());
      } catch (Exception e) {
        log.error("USER_ANONYMIZATION_FAILED: userId={}", user.getId(), e);
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    return new TriggerAnonymizationResponse(count, duration, "COMPLETED");
  }

  public void anonymizeUser(User user) {
    String userId = user.getId();
    revokeAllUserSessions(userId);
    redisTemplate.delete("user:last_login:" + userId);
    redisTemplate.delete("login_lockout:" + userId);
    redisTemplate.delete("login_attempts:" + userId);

    user.setUsername("deleted_user_" + userId);
    user.setEmail("deleted_" + userId + "@pwbmini.com");
    user.setPassword(null);
    user.setFullName(null);
    user.setPhone(null);
    user.setAvatarUrl(null);
    user.setOauthProvider(null);
    user.setOauthId(null);
    user.setStatus(UserStatus.ANONYMIZED);
    user.setDeleted(true);
    user.setDeletedAt(Instant.now());

    userRepository.save(user);

    OutboxEvent outboxEvent = new OutboxEvent();
    outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
    outboxEvent.setAggregateId(userId);
    outboxEvent.setEventType("ACCOUNT_ANONYMIZED");

    Map<String, Object> payload = new HashMap<>();
    payload.put("userId", userId);
    payload.put("email", "deleted_" + userId + "@pwbmini.com");
    payload.put("status", "ANONYMIZED");
    payload.put("locale", LocaleContextHolder.getLocale().getLanguage());

    try {
      outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize outbox event payload");
    }

    outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
    outboxEvent.setStatus(OutboxEventStatus.PENDING);
    OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
    eventPublisher.publishEvent(new OutboxCreatedEvent(savedEvent.getId()));
  }

  private User handleOauthAccountLinker(String email, String sub, String name, String picture) {
    Optional<User> existingUserOpt = userRepository.findByEmailAndDeletedFalse(email);

    if (existingUserOpt.isPresent()) {
      User user = existingUserOpt.get();

      if (user.getStatus() == UserStatus.BANNED) {
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
      }

      if (user.getOauthProvider() == OAuthProvider.GOOGLE) {
        user.setFullName(name);
        user.setAvatarUrl(picture);
        return userRepository.save(user);
      } else {
        if (user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.PENDING_DELETION) {
          user.setOauthProvider(OAuthProvider.GOOGLE);
          user.setOauthId(sub);
          user.setAvatarUrl(picture);
          return userRepository.save(user);
        } else if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
          User activatedUser = transactionTemplate.execute(status -> {
            otpService.deleteAllOtpKeys(email);
            user.setStatus(UserStatus.ACTIVE);
            user.setPassword(null);
            user.setOauthProvider(OAuthProvider.GOOGLE);
            user.setOauthId(sub);
            user.setFullName(name);
            user.setAvatarUrl(picture);
            return userRepository.save(user);
          });
          if (activatedUser != null) {
            createWelcomeEmailOutbox(activatedUser);
          }
          return activatedUser;
        }
      }
    }

    String baseUsername = email.substring(0, email.indexOf("@"));
    String username = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8);

    Role defaultRole = roleRepository.findByName(ROLE_USER)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
            "Default role USER not found"));

    User newUser = new User();
    newUser.setUsername(username);
    newUser.setEmail(email);
    newUser.setFullName(name);
    newUser.setAvatarUrl(picture);
    newUser.setStatus(UserStatus.ACTIVE);
    newUser.setPassword(null);
    newUser.setOauthProvider(OAuthProvider.GOOGLE);
    newUser.setOauthId(sub);
    newUser.setRole(defaultRole);

    try {
      User savedNewUser = userRepository.save(newUser);
      createWelcomeEmailOutbox(savedNewUser);
      return savedNewUser;
    } catch (DataIntegrityViolationException ex) {
      newUser.setUsername(baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8));
      User savedNewUser = userRepository.save(newUser);
      createWelcomeEmailOutbox(savedNewUser);
      return savedNewUser;
    }
  }

  private LoginResponse generateSessionAndResponse(User user, HttpServletResponse httpResponse) {
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = jwtService.generateRefreshToken();

    String zsetKey = "user:sessions:" + user.getId();
    long currentTimestamp = Instant.now().getEpochSecond();
    long refreshTokenExpiry = iamProperties.getJwt().getRefreshTokenExpiration();

    List<String> keys = List.of(zsetKey);
    Object[] args = new Object[]{refreshToken, String.valueOf(currentTimestamp), "3", String.valueOf(refreshTokenExpiry)};

    List<String> kickedTokens = redisTemplate.execute(concurrentSessionScript, keys, args);

    if (kickedTokens != null) {
      for (String kickedToken : kickedTokens) {
        redisTemplate.delete(SESSION_KEY_PREFIX + kickedToken);
        log.warn("Session kicked out: userId={}, token={}", user.getId(), kickedToken);
      }
    }

    redisTemplate.opsForValue().set(
        SESSION_KEY_PREFIX + refreshToken,
        user.getId(),
        Duration.ofSeconds(refreshTokenExpiry));

    String ipAddress = getClientIp();
    String userAgent = getUserAgent();
    String location = geoIpService.getLocation(ipAddress);
    String activeJwtSignature = jwtService.getSignature(accessToken);

    String browser = userAgent;
    String os = "Unknown";
    if (userAgent != null) {
      if (userAgent.contains("Windows")) os = "Windows";
      else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS")) os = "macOS";
      else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) os = "iOS";
      else if (userAgent.contains("Android")) os = "Android";
      else if (userAgent.contains("Linux")) os = "Linux";
    }

    String metadataKey = "session:metadata:" + refreshToken;
    Map<String, String> metadata = new HashMap<>();
    metadata.put("ip", ipAddress);
    metadata.put("browser", browser);
    metadata.put("os", os);
    metadata.put("location", location);
    metadata.put("createdAt", Instant.now().toString());
    metadata.put("active_jwt_signature", activeJwtSignature);

    redisTemplate.opsForHash().putAll(metadataKey, metadata);
    redisTemplate.expire(metadataKey, refreshTokenExpiry, TimeUnit.SECONDS);

    setRefreshCookie(httpResponse, refreshToken, (int) refreshTokenExpiry);

    eventPublisher.publishEvent(new LoginSuccessEvent(
        this, user.getId(), getClientIp(), getUserAgent()));

    return new LoginResponse(
        accessToken,
        iamProperties.getJwt().getAccessTokenExpiration(),
        new LoginResponse.UserInfo(
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().getName(),
            user.getStatus().name()
        )
    );
  }

  private String getClientIp() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      HttpServletRequest request = attributes.getRequest();
      String ip = request.getHeader("X-Forwarded-For");
      if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        return ip.split(",")[0].trim();
      }
      return request.getRemoteAddr();
    }
    return "Unknown";
  }

  private String getUserAgent() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      HttpServletRequest request = attributes.getRequest();
      return request.getHeader("User-Agent");
    }
    return "Unknown";
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
      Map<String, Object> payloadMap = new HashMap<>();
      payloadMap.put("eventType", EVENT_TYPE_REGISTRATION_OTP);
      payloadMap.put("email", email);
      payloadMap.put("otpCode", otpCode);
      payloadMap.put("fullName", fullName != null ? fullName : "");
      payloadMap.put("locale", LocaleContextHolder.getLocale().getLanguage());

      String payload = objectMapper.writeValueAsString(payloadMap);

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

  private void createWelcomeEmailOutbox(User user) {
    try {
      Map<String, Object> payloadMap = new HashMap<>();
      payloadMap.put("eventType", EVENT_TYPE_WELCOME_EMAIL);
      payloadMap.put("email", user.getEmail());
      payloadMap.put("fullName", user.getFullName() != null ? user.getFullName() : "");
      payloadMap.put("userId", user.getId());
      payloadMap.put("locale", LocaleContextHolder.getLocale().getLanguage());

      String payload = objectMapper.writeValueAsString(payloadMap);

      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
      outboxEvent.setAggregateId(user.getId());
      outboxEvent.setEventType(EVENT_TYPE_WELCOME_EMAIL);
      outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
      outboxEvent.setPayload(payload);
      outboxEvent.setStatus(OutboxEventStatus.PENDING);

      OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
      eventPublisher.publishEvent(new OutboxCreatedEvent(savedEvent.getId()));
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize welcome email outbox event payload", ex);
    }
  }

  private VerifyOtpResponse generateVerifyOtpResponse(User user, HttpServletResponse httpResponse) {
    LoginResponse loginResponse = generateSessionAndResponse(user, httpResponse);
    return new VerifyOtpResponse(
        loginResponse.accessToken(),
        loginResponse.expiresIn(),
        userMapper.toUserInfo(user));
  }

  private void setRefreshCookie(HttpServletResponse response, String value, int maxAge) {
    Cookie refreshCookie = new Cookie("refreshToken", value);
    refreshCookie.setHttpOnly(true);
    refreshCookie.setSecure(true);
    refreshCookie.setPath("/");
    refreshCookie.setMaxAge(maxAge);
    refreshCookie.setAttribute("SameSite", "Strict");
    response.addCookie(refreshCookie);
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    setRefreshCookie(response, "", 0);
  }

  private long calculateBlacklistTtl(Claims claims) {
    long currentTimeSeconds = Instant.now().getEpochSecond();
    long expTimeSeconds = claims.getExpiration().getTime() / 1000;
    long diff = expTimeSeconds - currentTimeSeconds;
    return Math.max(30, diff + 30);
  }

  private void revokeAllUserSessions(String userId) {
    String zsetKey = "user:sessions:" + userId;
    Set<String> sessionTokens = redisTemplate.opsForZSet().range(zsetKey, 0, -1);
    if (sessionTokens != null && !sessionTokens.isEmpty()) {
      long blacklistTtl = iamProperties.getJwt().getAccessTokenExpiration() + 30;
      List<String> keysToDelete = new ArrayList<>();
      for (String t : sessionTokens) {
        blacklistSessionJwt(t, blacklistTtl);
        keysToDelete.add("session:refresh_token:" + t);
        keysToDelete.add("session:metadata:" + t);
      }
      keysToDelete.add(zsetKey);
      redisTemplate.delete(keysToDelete);
    }
  }

  private void blacklistSessionJwt(String refreshToken, long ttlSeconds) {
    String metadataKey = "session:metadata:" + refreshToken;
    String signature = (String) redisTemplate.opsForHash().get(metadataKey, "active_jwt_signature");
    if (signature != null && !signature.isEmpty()) {
      redisTemplate.opsForValue().set(
          "session:blacklist_token:" + signature, "true", Duration.ofSeconds(ttlSeconds));
    }
  }
}
