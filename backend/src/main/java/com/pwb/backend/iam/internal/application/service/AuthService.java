package com.pwb.backend.iam.internal.application.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.backend.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.backend.iam.api.dto.request.LoginRequest;
import com.pwb.backend.iam.api.dto.request.Oauth2LoginRequest;
import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.backend.iam.api.dto.request.ResendOtpRequest;
import com.pwb.backend.iam.api.dto.request.UpdateProfileRequest;
import com.pwb.backend.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.backend.iam.api.dto.response.AvatarUploadResponse;
import com.pwb.backend.iam.api.dto.response.CheckUsernameResponse;
import com.pwb.backend.iam.api.dto.response.LoginResponse;
import com.pwb.backend.iam.api.dto.response.OAuthLinkPasswordRequiredData;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.RegistrationInProgressData;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import com.pwb.backend.iam.internal.domain.enums.OAuthProvider;
import com.pwb.backend.iam.internal.domain.enums.UserStatus;
import com.pwb.backend.iam.internal.application.factory.OutboxEventFactory;
import com.pwb.backend.iam.internal.application.helper.DisposableEmailChecker;
import com.pwb.backend.iam.internal.application.helper.JwtPrincipalExtractor;
import com.pwb.backend.iam.internal.application.helper.LoginLockoutHelper;
import com.pwb.backend.iam.internal.application.helper.OpaqueTokenGenerator;
import com.pwb.backend.iam.internal.application.helper.PiiScrubber;
import com.pwb.backend.iam.internal.application.mapper.UserMapper;
import com.pwb.backend.iam.internal.domain.model.Role;
import com.pwb.backend.iam.internal.domain.model.User;
import com.pwb.backend.iam.internal.infrastructure.repository.RoleRepository;
import com.pwb.backend.iam.internal.infrastructure.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String STATUS_ACTIVE_LABEL = "ACTIVE";
    private static final String PROVIDER_LOCAL_LABEL = "LOCAL";
    private static final String OAUTH_ISSUER_PRIMARY = "https://accounts.google.com";
    private static final String OAUTH_ISSUER_LEGACY = "accounts.google.com";
    private static final String OAUTH_AZP_CLAIM = "azp";
    private static final String OAUTH_NAME_CLAIM = "name";
    private static final String OAUTH_PICTURE_CLAIM = "picture";
    private static final String DUMMY_BCRYPT_PREFIX = "$2a$12$";
    private static final String DUMMY_BCRYPT_PAYLOAD = "C";
    private static final int DUMMY_BCRYPT_PAYLOAD_LENGTH = 53;
    private static final String RESET_TOKEN_REDIS_PREFIX = "password_reset_token:";
    private static final String FORGOT_PW_ATTEMPTS_EMAIL_PREFIX = "forgot_pw_attempts:email:";
    private static final String FORGOT_PW_LOCKOUT_EMAIL_PREFIX = "forgot_pw_lockout:email:";
    private static final String FORGOT_PW_ATTEMPTS_IP_PREFIX = "forgot_pw_attempts:ip:";
    private static final String FORGOT_PW_LOCKOUT_IP_PREFIX = "forgot_pw_lockout:ip:";
    private static final int FORGOT_PW_EMAIL_LIMIT = 5;
    private static final int FORGOT_PW_IP_LIMIT = 20;
    private static final Duration FORGOT_PW_WINDOW = Duration.ofMinutes(15);
    private static final long FORGOT_PW_DELAY_MILLIS = 500L;
    private static final long FORGOT_PW_JITTER_MILLIS = 50L;
    private static final long LOCK_WAIT_SECONDS = 3L;
    private static final long LOCK_LEASE_SECONDS = 10L;

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
    private final OutboxEventFactory outboxEventFactory;
    private final LoginLockoutHelper loginLockoutHelper;
    private final ClientIpResolver clientIpResolver;
    private final AvatarUploadService avatarUploadService;

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
            return resumePendingRegistration(existingPending.get(), request, normalizedEmail, normalizedUsername);
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

        return createFreshPendingRegistration(request, normalizedEmail);
    }

    private RegisterResponse resumePendingRegistration(
        User pendingUser, RegisterRequest request, String normalizedEmail, String normalizedUsername) {
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

    private RegisterResponse createFreshPendingRegistration(RegisterRequest request, String normalizedEmail) {
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
        boolean syntacticallyValid = username != null
            && !username.isBlank()
            && username.length() >= 3
            && username.length() <= 50;
        if (syntacticallyValid) {
            userRepository.existsByUsernameAndStatusAndDeletedFalse(username, UserStatus.ACTIVE);
        }
        return new CheckUsernameResponse(username, syntacticallyValid);
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request,
                                        HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().trim().toLowerCase();
        String lockKey = "lock:otp:verify:" + normalizedEmail;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
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

    private VerifyOtpResponse executeVerifyOtp(String normalizedEmail,
                                                VerifyOtpRequest request,
                                                HttpServletResponse httpResponse) {
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
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
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

    private void executeResendOtp(String normalizedEmail) {
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

        User user = userOpt.orElseGet(() -> {
            runDummyPasswordComparison(request.password());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS,
                "Incorrect username or password");
        });

        rejectLoginIfAccountNotAllowed(user);
        loginLockoutHelper.ensureNotLocked(redisTemplate, user.getId());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginLockoutHelper.recordFailure(redisTemplate, user.getId());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Incorrect username or password");
        }

        enforceMinimumLoginLatency(startNanos);

        redisTemplate.delete(loginLockoutHelper.attemptsKey(user.getId()));
        return generateSessionAndResponse(user, httpResponse);
    }

    private void runDummyPasswordComparison(String rawPassword) {
        String dummyHash = DUMMY_BCRYPT_PREFIX + DUMMY_BCRYPT_PAYLOAD.repeat(DUMMY_BCRYPT_PAYLOAD_LENGTH);
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    private void enforceMinimumLoginLatency(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long minNanos = 50_000_000L;
        if (elapsedNanos < minNanos) {
            try {
                Thread.sleep((minNanos - elapsedNanos) / 1_000_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void rejectLoginIfAccountNotAllowed(User user) {
        UserStatus status = user.getStatus();
        if (status == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account has been banned");
        }
        if (status == UserStatus.FROZEN) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is frozen");
        }
        if (status == UserStatus.PENDING_DELETION) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED,
                "Account deletion has been requested");
        }
        if (status == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(
                ErrorCode.REGISTRATION_IN_PROGRESS,
                "Please verify your account",
                new RegistrationInProgressData(
                    "/verify-otp",
                    user.getEmail()
                )
            );
        }
    }

    public LoginResponse loginWithGoogle(Oauth2LoginRequest request, HttpServletResponse httpResponse) {
        String ip = clientIpResolver.current();
        String ipKey = ip == null ? "unknown" : ip;
        String oauthLockoutKey = "oauth_lockout:ip:" + ipKey;
        loginLockoutHelper.ensureNotLockedKey(redisTemplate, oauthLockoutKey);

        GoogleIdToken.Payload payload = verifyGoogleIdToken(request.idToken());
        User user = transactionTemplate.execute(status ->
            handleOauthAccountLinker(
                payload.getEmail().trim().toLowerCase(),
                payload.getSubject(),
                (String) payload.get(OAUTH_NAME_CLAIM),
                (String) payload.get(OAUTH_PICTURE_CLAIM),
                request.linkingPassword()));

        rejectLoginIfOAuthAccountNotAllowed(user, user.getStatus());
        loginLockoutHelper.ensureNotLocked(redisTemplate, user.getId());
        return generateSessionAndResponse(user, httpResponse);
    }

    private GoogleIdToken.Payload verifyGoogleIdToken(String idToken) {
        if (googleVerifier == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Google Verifier is not initialized");
        }

        GoogleIdToken googleIdToken;
        try {
            googleIdToken = googleVerifier.verify(idToken);
        } catch (Exception e) {
            log.error("Google token verification failed");
            recordOAuthFailure(idToken);
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "Invalid OAuth token, please try again");
        }

        if (googleIdToken == null) {
            recordOAuthFailure(idToken);
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "Invalid OAuth token, please try again");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        validateGoogleIdTokenPayload(payload);
        return payload;
    }

    private void recordOAuthFailure(String idToken) {
        String ip = clientIpResolver.current();
        String ipKey = ip == null ? "unknown" : ip;
        String bucket = "oauth_failures:ip:" + ipKey;
        String lockoutKey = "oauth_lockout:ip:" + ipKey;
        loginLockoutHelper.recordFailure(redisTemplate, bucket, lockoutKey,
            iamProperties.getLogin().getLockout().getMaxAttempts(),
            loginLockoutHelper.getWindowDuration());
    }

    private void validateGoogleIdTokenPayload(GoogleIdToken.Payload payload) {
        String issuer = payload.getIssuer();
        if (!issuer.equals(OAUTH_ISSUER_PRIMARY) && !issuer.equals(OAUTH_ISSUER_LEGACY)) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "Invalid OAuth token issuer");
        }

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "Google email is not verified");
        }

        enforceAuthorizedPartyMatches(payload);
    }

    private void enforceAuthorizedPartyMatches(GoogleIdToken.Payload payload) {
        String configuredClientId = iamProperties.getGoogle() != null
            ? iamProperties.getGoogle().getClientId()
            : null;
        if (configuredClientId == null || configuredClientId.isBlank()) {
            return;
        }
        String azp = (String) payload.get(OAUTH_AZP_CLAIM);
        if (azp != null && !azp.equals(configuredClientId)) {
            log.warn("Google OAuth azp mismatch");
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "OAuth token authorized party does not match this application");
        }
    }

    private void rejectLoginIfOAuthAccountNotAllowed(User user, UserStatus status) {
        if (status == UserStatus.BANNED
            || status == UserStatus.FROZEN
            || status == UserStatus.PENDING_DELETION) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
        }
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        long startTime = System.currentTimeMillis();

        String normalizedEmail = request.email() == null ? "" : request.email().trim().toLowerCase();
        String ipBucket = clientIpResolver.current();
        String perEmailAttemptsKey = FORGOT_PW_ATTEMPTS_EMAIL_PREFIX + sha256(normalizedEmail);
        String perEmailLockoutKey = FORGOT_PW_LOCKOUT_EMAIL_PREFIX + sha256(normalizedEmail);
        String perIpAttemptsKey = FORGOT_PW_ATTEMPTS_IP_PREFIX + ipBucket;
        String perIpLockoutKey = FORGOT_PW_LOCKOUT_IP_PREFIX + ipBucket;

        loginLockoutHelper.ensureNotLockedKey(redisTemplate, perEmailLockoutKey);
        loginLockoutHelper.ensureNotLockedKey(redisTemplate, perIpLockoutKey);

        Optional<User> userOpt = normalizedEmail.isBlank()
            ? Optional.empty()
            : userRepository.findByEmailAndDeletedFalse(normalizedEmail);

        User realUser = userOpt
            .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getPassword() != null)
            .orElse(null);

        if (realUser == null) {
            sleepUntil(startTime, FORGOT_PW_DELAY_MILLIS);
            boolean existsButNotAllowed = userOpt.isPresent()
                && userOpt.get().getStatus() != UserStatus.ACTIVE;
            if (!existsButNotAllowed) {
                loginLockoutHelper.recordFailure(redisTemplate, perEmailAttemptsKey, perEmailLockoutKey,
                    FORGOT_PW_EMAIL_LIMIT, FORGOT_PW_WINDOW);
                loginLockoutHelper.recordFailure(redisTemplate, perIpAttemptsKey, perIpLockoutKey,
                    FORGOT_PW_IP_LIMIT, FORGOT_PW_WINDOW);
            }
            return;
        }

        dispatchPasswordReset(realUser);
        redisTemplate.delete(perEmailAttemptsKey);
    }

    private void dispatchPasswordReset(User realUser) {
        String token = OpaqueTokenGenerator.generate();
        long ttlSeconds = iamProperties.getLogin().getPasswordResetTokenTtlSeconds();

        redisTemplate.opsForValue().set(RESET_TOKEN_REDIS_PREFIX + token, realUser.getEmail(),
            Duration.ofSeconds(ttlSeconds));

        transactionTemplate.executeWithoutResult(status ->
            outboxEventFactory.passwordReset(realUser, token,
                LocaleContextHolder.getLocale().getLanguage()));
    }

    private String sha256(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available in JDK runtime", e);
        }
    }

    private void sleepUntil(long startMillis, long targetMillis) {
        long duration = System.currentTimeMillis() - startMillis;
        if (duration < targetMillis) {
            try {
                long delay = (targetMillis - duration) + (long) (Math.random() * FORGOT_PW_JITTER_MILLIS);
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

        String tokenKey = RESET_TOKEN_REDIS_PREFIX + request.token();
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

        sessionService.revokeAllUserSessions(user.getId());
        loginLockoutHelper.clear(redisTemplate, user.getId());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request,
                               String expiredAccessTokenHeader,
                               String currentRefreshToken) {
        String email = JwtPrincipalExtractor.requireEmailFromExpiredTokenHeader(
            expiredAccessTokenHeader, jwtService);

        User user = userRepository.findByEmailAndDeletedFalse(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
        }

        if (user.getPassword() == null) {
            throw new BusinessException(ErrorCode.OAUTH_ONLY_ACCOUNT,
                "Cannot change password for OAuth-only account");
        }

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD, "Incorrect old password");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_REUSE_BLOCKED,
                "New password must be different from the old password");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Passwords do not match");
        }

        transactionTemplate.executeWithoutResult(status -> {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
        });

        sessionService.revokeOtherSessions(expiredAccessTokenHeader, currentRefreshToken);
        loginLockoutHelper.clear(redisTemplate, user.getId());
    }

    public UserProfileResponse getMyProfile(String authHeader) {
        String email = JwtPrincipalExtractor.requireEmailFromHeader(authHeader, jwtService);
        User user = userRepository.findByEmailAndDeletedFalse(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));
        return userMapper.toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request, String authHeader) {
        String email = JwtPrincipalExtractor.requireEmailFromHeader(authHeader, jwtService);
        User user = userRepository.findByEmailAndDeletedFalse(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_EXISTED, "User does not exist"));

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
    public AvatarUploadResponse uploadAvatar(String authHeader, MultipartFile file) {
        String email = JwtPrincipalExtractor.requireEmailFromHeader(authHeader, jwtService);
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

    private User handleOauthAccountLinker(String email,
                                          String sub,
                                          String name,
                                          String picture,
                                          String linkingPassword) {
        Optional<User> existingUserOpt = userRepository.findByEmailAndDeletedFalse(email);

        if (existingUserOpt.isPresent()) {
            return linkOrActivate(existingUserOpt.get(), email, sub, name, picture, linkingPassword);
        }

        return createNewOauthUser(email, sub, name, picture);
    }

    private User linkOrActivate(User user,
                                String email,
                                String sub,
                                String name,
                                String picture,
                                String linkingPassword) {
        UserStatus status = user.getStatus();
        if (status == UserStatus.BANNED || status == UserStatus.FROZEN) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
        }
        if (status == UserStatus.PENDING_DELETION) {
            log.warn("Refused silent OAuth link to PENDING_DELETION account");
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED,
                "Account deletion has been requested");
        }

        if (user.getOauthProvider() == OAuthProvider.GOOGLE) {
            return relinkExistingGoogleAccount(user, sub, name, picture);
        }

        if (status == UserStatus.ACTIVE) {
            return linkPasswordToExistingLocalUser(user, email, sub, name, picture, linkingPassword);
        }
        if (status == UserStatus.PENDING_VERIFICATION) {
            return activatePendingUserWithGoogle(user, email, sub, name, picture);
        }
        throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Account is not active");
    }

    private User relinkExistingGoogleAccount(User user, String sub, String name, String picture) {
        if (!sub.equals(user.getOauthId())) {
            log.warn("OAuth sub mismatch for existing Google-linked account email={}", PiiScrubber.maskEmail(user.getEmail()));
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN,
                "OAuth identity does not match the account on file");
        }
        user.setFullName(name);
        user.setAvatarUrl(picture);
        return userRepository.save(user);
    }

    private User linkPasswordToExistingLocalUser(User user,
                                                 String email,
                                                 String sub,
                                                 String name,
                                                 String picture,
                                                 String linkingPassword) {
        if (linkingPassword == null || linkingPassword.isBlank()) {
            log.warn("Refused silent OAuth link to existing LOCAL account email={}", PiiScrubber.maskEmail(email));
            throw new BusinessException(
                ErrorCode.OAUTH_LINK_PASSWORD_REQUIRED,
                "This email is already registered. Provide the current account password to link Google sign-in.",
                new OAuthLinkPasswordRequiredData(email)
            );
        }
        if (user.getPassword() == null
            || !passwordEncoder.matches(linkingPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD,
                "Incorrect password for the existing LOCAL account");
        }
        user.setOauthProvider(OAuthProvider.GOOGLE);
        user.setOauthId(sub);
        user.setAvatarUrl(picture);
        return userRepository.save(user);
    }

    private User activatePendingUserWithGoogle(User user,
                                               String email,
                                               String sub,
                                               String name,
                                               String picture) {
        otpService.deleteAllOtpKeys(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setPassword(null);
        user.setOauthProvider(OAuthProvider.GOOGLE);
        user.setOauthId(sub);
        user.setFullName(name);
        user.setAvatarUrl(picture);
        User activatedUser = userRepository.save(user);
        outboxEventFactory.welcomeEmail(activatedUser, LocaleContextHolder.getLocale().getLanguage());
        return activatedUser;
    }

private User createNewOauthUser(String email, String sub, String name, String picture) {
    String baseUsername = email.substring(0, email.indexOf("@"));

    Role defaultRole = roleRepository.findByName(ROLE_USER)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
            "Default role USER not found"));

    int maxAttempts = 5;
    DataIntegrityViolationException lastError = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      User newUser = new User();
      newUser.setUsername(generateUniqueUsername(baseUsername, attempt));
      newUser.setEmail(email);
      newUser.setFullName(name);
      newUser.setAvatarUrl(picture);
      newUser.setStatus(UserStatus.ACTIVE);
      newUser.setPassword(null);
      newUser.setOauthProvider(OAuthProvider.GOOGLE);
      newUser.setOauthId(sub);
      newUser.setRole(defaultRole);

      try {
        return saveAndSendWelcomeEmail(newUser);
      } catch (DataIntegrityViolationException ex) {
        log.warn("Username collision on OAuth registration attempt {}/{}", attempt, maxAttempts);
        lastError = ex;
      }
    }
    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
        "Could not allocate a unique username after " + maxAttempts + " attempts",
        lastError);
  }

  private String generateUniqueUsername(String baseUsername, int attempt) {
    String hex12 = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return baseUsername + "_" + attempt + "_" + hex12;
  }

    private User saveAndSendWelcomeEmail(User user) {
        User savedNewUser = userRepository.save(user);
        outboxEventFactory.welcomeEmail(savedNewUser, LocaleContextHolder.getLocale().getLanguage());
        return savedNewUser;
    }

    private LoginResponse generateSessionAndResponse(User user, HttpServletResponse httpResponse) {
        sessionService.createSession(user, httpResponse);

        String accessToken = jwtService.generateAccessToken(user);

        eventPublisher.publishEvent(new LoginSuccessEvent(
            this, user.getId(), getClientIp(), getUserAgent()));

        return new LoginResponse(
            accessToken,
            iamProperties.getJwt().getAccessTokenExpiration(),
            new com.pwb.backend.shared.dto.UserInfoResponse(
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getStatus().name(),
                user.getOauthProvider() == null ? PROVIDER_LOCAL_LABEL : user.getOauthProvider().name()
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
