package com.pwb.backend.iam.internal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pwb.backend.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.backend.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.AvatarUploadResponse;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.OAuthLinkPasswordRequiredData;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.RegistrationInProgressData;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OAuthProvider;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.helper.DisposableEmailChecker;
import com.pwb.backend.iam.internal.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.helper.OpaqueTokenGenerator;
import com.pwb.backend.iam.internal.helper.PiiScrubber;
import com.pwb.backend.iam.internal.helper.SessionMetadataBuilder;
import com.pwb.backend.iam.internal.mapper.UserMapper;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.RoleRepository;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.security.ClientIpResolver;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ROLE_USER = "USER";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final OtpService otpService;
  private final JwtService jwtService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final IamProperties iamProperties;
  private final StringRedisTemplate redisTemplate;
  private final UserMapper userMapper;
  private final RedissonClient redissonClient;
  private final TransactionTemplate transactionTemplate;
  private final PlatformTransactionManager transactionManager;
  private final SessionService sessionService;
  private final AccountLifecycleService accountLifecycleService;
  private final com.pwb.backend.iam.internal.factory.OutboxEventFactory outboxEventFactory;
  private final LoginLockoutHelper loginLockoutHelper;
  private final ClientIpResolver clientIpResolver;

  private GoogleIdTokenVerifier googleVerifier;

  @PostConstruct
  public void init() {
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
    String normalizedUsername = request.username().trim().toLowerCase();

    if (DisposableEmailChecker.isDisposable(normalizedEmail)) {
      throw new BusinessException(ErrorCode.DISPOSABLE_EMAIL_NOT_ALLOWED,
          "Disposable email addresses are not allowed");
    }

    Optional<User> existingPending = userRepository.findPendingUserForUpdate(
        normalizedUsername, normalizedEmail);

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
      outboxEventFactory.registrationOtp(pendingUser, normalizedEmail, otpCode, pendingUser.getFullName(),
          LocaleContextHolder.getLocale().getLanguage());

      return userMapper.toRegisterResponse(pendingUser);
    }

    if (userRepository.existsByUsernameAndStatusAndDeletedFalse(
        normalizedUsername, UserStatus.ACTIVE)) {
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
      outboxEventFactory.registrationOtp(newUser, normalizedEmail, otpCode, newUser.getFullName(),
          LocaleContextHolder.getLocale().getLanguage());

      return userMapper.toRegisterResponse(newUser);
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.REGISTRATION_IN_PROGRESS,
          "Registration is already in progress for this account");
    }
  }

  public CheckUsernameResponse checkUsernameAvailability(String username) {
    // SECURITY (CRIT-2): the public /check-username endpoint used to return
    // a real `available: false` for existing usernames, enabling offline
    // username enumeration. We now always return `available: true` and let
    // the actual existence check happen atomically at /register. To avoid
    // leaking timing differences the request still does a fixed-cost DB
    // existence lookup (the query result is intentionally ignored).
    if (username == null || username.isBlank() || username.length() < 3 || username.length() > 50) {
      return new CheckUsernameResponse(username, false);
    }
    userRepository.existsByUsernameAndStatusAndDeletedFalse(username, UserStatus.ACTIVE);
    return new CheckUsernameResponse(username, true);
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

    if (!otpService.verifyOtp(normalizedEmail, request.otpCode())) {
      otpService.incrementAttempts(normalizedEmail);
      throw new BusinessException(ErrorCode.INVALID_OTP, "Invalid OTP code");
    }

    otpService.deleteAllOtpKeys(normalizedEmail);

    User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED,
            "User does not exist"));

    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);

    outboxEventFactory.welcomeEmail(user, LocaleContextHolder.getLocale().getLanguage());

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
    outboxEventFactory.registrationOtp(user, normalizedEmail, otpCode, user.getFullName(),
        LocaleContextHolder.getLocale().getLanguage());
  }

  public LoginResponse login(LoginRequest request, HttpServletResponse httpResponse) {
    long startNanos = System.nanoTime();
    String usernameOrEmail = request.usernameOrEmail();

    Optional<User> userOpt = userRepository.findByUsernameOrEmailAndDeletedFalse(usernameOrEmail);

    // SECURITY (HIGH-3): equalize timing for unknown vs known accounts by
    // always running a BCrypt comparison, even when the user does not exist.
    User user = userOpt.orElseGet(() -> {
      String dummyHash = "$2a$12$" + "C".repeat(53);
      passwordEncoder.matches(request.password() == null ? "" : request.password(), dummyHash);
      throw new BusinessException(ErrorCode.BAD_CREDENTIALS,
          "Incorrect username or password");
    });

    String userId = user.getId();

    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
    }

    if (user.getStatus() == UserStatus.FROZEN) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is frozen");
    }

    if (user.getStatus() == UserStatus.PENDING_DELETION) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED,
          "Account deletion has been requested");
    }

    if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
      throw new BusinessException(
          ErrorCode.REGISTRATION_IN_PROGRESS,
          "Please verify your account",
          new RegistrationInProgressData(
              "/verify-otp",
              user.getEmail()
          )
      );
    }

    loginLockoutHelper.ensureNotLocked(redisTemplate, userId);

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      loginLockoutHelper.recordFailure(redisTemplate, userId);
      throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Incorrect username or password");
    }

    // Equalize timing: ensure the slow path is at least as long as the
    // happy path so attackers cannot use latency as a side-channel to
    // identify existing usernames.
    long elapsedNanos = System.nanoTime() - startNanos;
    long minNanos = 50_000_000L; // ~50ms floor
    if (elapsedNanos < minNanos) {
      try {
        Thread.sleep((minNanos - elapsedNanos) / 1_000_000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    redisTemplate.delete(loginLockoutHelper.attemptsKey(userId));

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
      log.error("Google token verification failed");
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Invalid OAuth token, please try again");
    }

    if (idToken == null) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Invalid OAuth token, please try again");
    }

    GoogleIdToken.Payload payload = idToken.getPayload();

    String issuer = payload.getIssuer();
    if (!issuer.equals("https://accounts.google.com") && !issuer.equals("accounts.google.com")) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Invalid OAuth token issuer");
    }

    if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "Google email is not verified");
    }

    // SECURITY (MED-9): enforce azp (authorized party) so a token minted for
    // some other client of ours cannot be replayed at this endpoint. The
    // configured Google client id is the only authorized party.
    String azp = (String) payload.get("azp");
    String configuredClientId = iamProperties.getGoogle() != null
        ? iamProperties.getGoogle().getClientId()
        : null;
    if (configuredClientId != null && !configuredClientId.isBlank()
        && azp != null && !azp.equals(configuredClientId)) {
      log.warn("Google OAuth azp mismatch");
      throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
          "OAuth token authorized party does not match this application");
    }

    String email = payload.getEmail().trim().toLowerCase();
    String sub = payload.getSubject();
    String name = (String) payload.get("name");
    String picture = (String) payload.get("picture");

    // SECURITY (CRIT-1): an account in PENDING_DELETION must NEVER be
    // auto-recovered via OAuth. The account is in a 30-day grace period
    // and the legitimate owner may want to cancel deletion. We refuse
    // OAuth logins for these accounts outright.
    Optional<User> existingOpt = userRepository.findByEmailAndDeletedFalse(email);
    if (existingOpt.isPresent()) {
      User existing = existingOpt.get();
      if (existing.getStatus() == UserStatus.PENDING_DELETION) {
        log.warn("Refused OAuth login for PENDING_DELETION account");
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED,
            "Account deletion has been requested");
      }
      if (existing.getStatus() == UserStatus.FROZEN
          || existing.getStatus() == UserStatus.BANNED) {
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
      }
    }

    User user = handleOauthAccountLinker(email, sub, name, picture, request.linkingPassword());

    String userId = user.getId();
    loginLockoutHelper.ensureNotLocked(redisTemplate, userId);

    if (user.getStatus() == UserStatus.BANNED
        || user.getStatus() == UserStatus.FROZEN
        || user.getStatus() == UserStatus.PENDING_DELETION) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
    }

    return generateSessionAndResponse(user, httpResponse);
  }

  public void forgotPassword(ForgotPasswordRequest request) {
    long startTime = System.currentTimeMillis();

    String normalizedEmail = request.email() == null ? "" : request.email().trim().toLowerCase();

    // SECURITY: per-email and per-IP rate limit on /forgot-password. The
    // bucketed response always mirrors the success path so attackers cannot
    // distinguish "this email is registered" from "this email is not".
    String ipBucket = clientIpResolver.current();
    String perEmailAttemptsKey = "forgot_pw_attempts:email:" + sha256(normalizedEmail);
    String perEmailLockoutKey = "forgot_pw_lockout:email:" + sha256(normalizedEmail);
    String perIpAttemptsKey = "forgot_pw_attempts:ip:" + ipBucket;
    String perIpLockoutKey = "forgot_pw_lockout:ip:" + ipBucket;

    loginLockoutHelper.ensureNotLockedKey(redisTemplate, perEmailLockoutKey);
    loginLockoutHelper.ensureNotLockedKey(redisTemplate, perIpLockoutKey);

    Optional<User> userOpt = normalizedEmail.isBlank()
        ? Optional.empty()
        : userRepository.findByEmailAndDeletedFalse(normalizedEmail);

    User realUser = userOpt.filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getPassword() != null)
        .orElse(null);

    if (realUser == null) {
      // Fixed-cost delay + record failure so timing/limit patterns are
      // indistinguishable from the success path.
      sleepUntil(startTime, 500);
      loginLockoutHelper.recordFailure(redisTemplate, perEmailAttemptsKey, perEmailLockoutKey,
          5, Duration.ofMinutes(15));
      loginLockoutHelper.recordFailure(redisTemplate, perIpAttemptsKey, perIpLockoutKey,
          20, Duration.ofMinutes(15));
      return;
    }

    // SECURITY (HIGH-5): use a high-entropy opaque token stored in Redis,
    // NOT a JWT. The previous JWT-based token was re-usable within its TTL
    // and inherited the access-token signing key's blast radius.
    String token = OpaqueTokenGenerator.generate();
    long ttlSeconds = iamProperties.getLogin().getPasswordResetTokenTtlSeconds();

    redisTemplate.opsForValue().set("password_reset_token:" + token, realUser.getEmail(),
        Duration.ofSeconds(ttlSeconds));

    transactionTemplate.executeWithoutResult(status ->
        outboxEventFactory.passwordReset(realUser, token, LocaleContextHolder.getLocale().getLanguage()));

    // Reset per-email counter on successful dispatch (the previous attempts
    // were legitimate attempts by the real owner).
    redisTemplate.delete(perEmailAttemptsKey);
  }

  private String sha256(String s) {
    if (s == null || s.isEmpty()) return "";
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      return Integer.toHexString(s.hashCode());
    }
  }

  private void sleepUntil(long startMillis, long targetMillis) {
    long duration = System.currentTimeMillis() - startMillis;
    if (duration < targetMillis) {
      try {
        long delay = (targetMillis - duration) + (long) (Math.random() * 50);
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Transactional
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

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
    }

    transactionTemplate.executeWithoutResult(status -> {
      user.setPassword(passwordEncoder.encode(request.newPassword()));
      userRepository.save(user);
    });

    String userId = user.getId();
    sessionService.revokeAllUserSessions(userId);

    loginLockoutHelper.clear(redisTemplate, userId);
  }

  @Transactional
  public void changePassword(ChangePasswordRequest request, String expiredAccessTokenHeader, String currentRefreshToken) {
    String email = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
        .requireEmailFromExpiredTokenHeader(expiredAccessTokenHeader, jwtService);

    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
    }

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
    sessionService.revokeOtherSessions(expiredAccessTokenHeader, currentRefreshToken);

    loginLockoutHelper.clear(redisTemplate, userId);
  }

  public UserProfileResponse getMyProfile(String authHeader) {
    String email = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
        .requireEmailFromHeader(authHeader, jwtService);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));
    return userMapper.toUserProfileResponse(user);
  }

  @Transactional
  public UserProfileResponse updateProfile(UpdateProfileRequest request, String authHeader) {
    String email = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
        .requireEmailFromHeader(authHeader, jwtService);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    // SECURITY (HIGH-4): an account in PENDING_DELETION must not be able to
    // mutate its own profile during the grace period. Allowing this would
    // confuse audit trails and could let an attacker overwrite identifying
    // data right before anonymization.
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Profile can only be updated while the account is active");
    }

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

  @Transactional
  public AvatarUploadResponse uploadAvatar(String authHeader, MultipartFile file, AvatarUploadService avatarUploadService) {
    String email = com.pwb.backend.iam.internal.helper.JwtPrincipalExtractor
        .requireEmailFromHeader(authHeader, jwtService);
    User user = userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Avatar can only be updated while the account is active");
    }

    String avatarUrl = avatarUploadService.uploadAvatar(user.getId(), file);
    user.setAvatarUrl(avatarUrl);
    userRepository.save(user);

    return new AvatarUploadResponse(avatarUrl);
  }

  @Transactional
  private User handleOauthAccountLinker(String email, String sub, String name, String picture, String linkingPassword) {
    Optional<User> existingUserOpt = userRepository.findByEmailAndDeletedFalse(email);

    if (existingUserOpt.isPresent()) {
      User user = existingUserOpt.get();

      if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.FROZEN) {
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
      }

      // CRIT-1: a LOCAL account in PENDING_DELETION must not be silently
      // re-linked. Either require linking password (if status is ACTIVE) or
      // refuse outright.
      if (user.getStatus() == UserStatus.PENDING_DELETION) {
        log.warn("Refused silent OAuth link to PENDING_DELETION account");
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED,
            "Account deletion has been requested");
      }

      if (user.getOauthProvider() == OAuthProvider.GOOGLE) {
        if (!sub.equals(user.getOauthId())) {
          log.warn("OAuth sub mismatch for existing Google-linked account email={}", PiiScrubber.maskEmail(email));
          throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
              "OAuth identity does not match the account on file");
        }
        user.setFullName(name);
        user.setAvatarUrl(picture);
        return userRepository.save(user);
      } else {
        if (user.getStatus() == UserStatus.ACTIVE) {
          if (linkingPassword == null || linkingPassword.isBlank()) {
            log.warn("Refused silent OAuth link to existing LOCAL account email={}", PiiScrubber.maskEmail(email));
            throw new BusinessException(
                ErrorCode.OAUTH_LINK_PASSWORD_REQUIRED,
                "This email is already registered. Provide the current account password to link Google sign-in.",
                new OAuthLinkPasswordRequiredData(email)
            );
          }
          if (user.getPassword() == null || !passwordEncoder.matches(linkingPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD,
                "Incorrect password for the existing LOCAL account");
          }
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
            outboxEventFactory.welcomeEmail(activatedUser, LocaleContextHolder.getLocale().getLanguage());
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
      outboxEventFactory.welcomeEmail(savedNewUser, LocaleContextHolder.getLocale().getLanguage());
      return savedNewUser;
    } catch (DataIntegrityViolationException ex) {
      newUser.setUsername(baseUsername + "_" + UUID.randomUUID().toString().substring(0, 8));
      User savedNewUser = userRepository.save(newUser);
      outboxEventFactory.welcomeEmail(savedNewUser, LocaleContextHolder.getLocale().getLanguage());
      return savedNewUser;
    }
  }

  private LoginResponse generateSessionAndResponse(User user, HttpServletResponse httpResponse) {
    sessionService.createSession(user, httpResponse);

    String accessToken = jwtService.generateAccessToken(user);

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
            user.getStatus().name(),
            user.getOauthProvider() == null ? "LOCAL" : user.getOauthProvider().name()
        )
    );
  }

  private String getClientIp() {
    return clientIpResolver.current();
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
    user.setUsername(request.username().trim().toLowerCase());
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setRole(defaultRole);

    return userRepository.save(user);
  }

  private void updatePendingUser(User user, RegisterRequest request, String normalizedEmail) {
    user.setUsername(request.username().trim().toLowerCase());
    user.setEmail(normalizedEmail);
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setFullName(request.fullName());

    userRepository.save(user);
  }

  private VerifyOtpResponse generateVerifyOtpResponse(User user, HttpServletResponse httpResponse) {
    sessionService.createSession(user, httpResponse);

    String accessToken = jwtService.generateAccessToken(user);

    return new VerifyOtpResponse(
        accessToken,
        iamProperties.getJwt().getAccessTokenExpiration(),
        userMapper.toUserInfo(user));
  }
}
