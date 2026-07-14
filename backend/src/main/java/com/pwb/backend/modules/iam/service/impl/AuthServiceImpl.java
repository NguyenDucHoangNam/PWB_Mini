package com.pwb.backend.modules.iam.service.impl;

import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.config.GeoIpConfig;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.outbox.OutboxService;
import com.pwb.backend.common.outbox.event.OtpResentEvent;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.common.outbox.event.UserVerifiedEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.security.captcha.CaptchaContext;
import com.pwb.backend.common.security.captcha.CaptchaVerifier;
import com.pwb.backend.common.security.jwt.JwtProperties;
import com.pwb.backend.common.security.jwt.JwtSigner;
import com.pwb.backend.common.util.DisposableEmailChecker;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.common.util.SecureRandomOtpGenerator;
import com.pwb.backend.modules.iam.dto.request.GoogleLoginRequest;
import com.pwb.backend.modules.iam.dto.request.LoginRequest;
import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.request.ResendOtpRequest;
import com.pwb.backend.modules.iam.dto.request.VerifyOtpRequest;
import com.pwb.backend.modules.iam.dto.response.LoginResponse;
import com.pwb.backend.modules.iam.dto.response.RefreshResponse;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.ResendOtpResponse;
import com.pwb.backend.modules.iam.dto.response.UserInfo;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.RoleType;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.event.LoginSuccessEvent;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.RoleRepository;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.AuthService;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.EmailRateLimiter;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.OtpService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String DUMMY_HASH = "$2a$12$" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmn";
    private static final int USERNAME_GENERATION_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OtpService otpService;
    private final PasswordHasher passwordHasher;
    private final SecureRandomOtpGenerator otpGenerator;
    private final UserMapper userMapper;
    private final JwtSigner jwtSigner;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final GoogleOAuthService googleOAuthService;
    private final OutboxService outboxService;
    private final DatabaseReader geoIpDatabaseReader;
    private final DisposableEmailChecker disposableEmailChecker;
    private final CaptchaVerifier captchaVerifier;
    private final EmailRateLimiter emailRateLimiter;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request, String ip) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        emailRateLimiter.checkRegisterAttempt(email);

        if (ip != null && !ip.isBlank()) {
            loginAttemptService.validateIpNotBlocked(ip);
        }

        try {
            if (disposableEmailChecker.isDisposable(email)) {
                recordRegisterFailure(ip);
                throw new BusinessException(IamErrorCode.EMAIL_DISPOSABLE);
            }
        } catch (BusinessException ex) {
            recordRegisterFailure(ip);
            throw ex;
        }

        User existing;
        try {
            existing = userRepository.findByEmailForUpdate(email).orElse(null);
        } catch (RuntimeException ex) {
            recordRegisterFailure(ip);
            throw ex;
        }

        User user;
        boolean isResend;
        if (existing == null) {
            Role userRole = roleRepository.findByCode(RoleType.USER.code())
                    .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND,
                            "Default USER role is missing"));
            user = User.newPending(
                    email,
                    passwordHasher.hash(request.password()),
                    request.fullName(),
                    userRole);
            user = saveUserWithUniqueUsername(user, email);
            isResend = false;
        } else if (existing.getStatus() == UserStatus.PENDING_VERIFICATION && existing.isLocal()) {
            boolean updateCredentials = request.password() != null
                    && !request.password().isBlank()
                    && request.fullName() != null
                    && !request.fullName().isBlank();
            if (updateCredentials && (request.otp() == null || request.otp().isBlank())) {
                throw new BusinessException(IamErrorCode.INVALID_OTP,
                        "OTP is required to update password or full name on pending registration");
            }
            if (updateCredentials) {
                boolean otpOk;
                try {
                    otpOk = otpService.verifyOtp(email, request.otp());
                } catch (BusinessException ex) {
                    throw ex;
                }
                if (!otpOk) {
                    throw new BusinessException(IamErrorCode.INVALID_OTP);
                }
                existing.setPasswordHash(passwordHasher.hash(request.password()));
                existing.setFullName(request.fullName());
            } else if (request.fullName() != null && !request.fullName().isBlank()) {
                existing.setFullName(request.fullName());
            }
            user = userRepository.save(existing);
            isResend = true;
        } else {
            recordRegisterFailure(ip);
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String otp = otpGenerator.generate();
        otpService.issueOtp(email, otp);

        publishUserRegistered(user, otp, Instant.now());

        if (isResend) {
            log.info("REGISTRATION_RESENT userId={} email={}", user.getId(), MaskingLogArg.email(email));
        }

        if (ip != null && !ip.isBlank()) {
            loginAttemptService.clearIpFailures(ip);
        }

        return userMapper.userToRegisterResponse(user);
    }

    private void recordRegisterFailure(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        loginAttemptService.recordIpFailure(ip);
    }

    @Override
    @Transactional
    public LoginResponse verifyOtp(VerifyOtpRequest request, String ip, String userAgent) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.USER_ALREADY_VERIFIED);
        }
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(IamErrorCode.USER_NOT_FOUND);
        }

        if (otpService.isLocked(email)) {
            throw BusinessException.builder()
                    .errorCode(IamErrorCode.OTP_LOCKED)
                    .customMessage("OTP is locked. Retry after " + otpService.lockoutRetryAfterSeconds() + " seconds")
                    .details(Map.of("retryAfterSeconds", otpService.lockoutRetryAfterSeconds()))
                    .build();
        }

        boolean ok;
        try {
            ok = otpService.verifyOtp(email, request.otp());
        } catch (BusinessException ex) {
            captchaVerifier.recordFailure(CaptchaContext.verifyOtp(email));
            throw ex;
        }
        if (!ok) {
            captchaVerifier.recordFailure(CaptchaContext.verifyOtp(email));
            throw new BusinessException(IamErrorCode.INVALID_OTP);
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        log.info("OTP_VERIFIED userId={} email={}", user.getId(), MaskingLogArg.email(email));

        loginAttemptService.clearFailures(user.getId());
        if (ip != null && !ip.isBlank()) {
            loginAttemptService.clearIpFailures(ip);
        }

        LoginResponse response = completeSuccessfulLogin(user, ip, userAgent);
        publishUserVerified(user, Instant.now());
        return response;
    }

    @Override
    @Transactional
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        emailRateLimiter.checkResendOtpAttempt(email);

        if (!otpService.tryAcquireResendSlot(email)) {
            throw BusinessException.builder()
                    .errorCode(IamErrorCode.OTP_RESEND_COOLDOWN)
                    .customMessage("Please wait before requesting a new OTP")
                    .details(Map.of("retryAfterSeconds", otpService.lockoutRetryAfterSeconds()))
                    .build();
        }

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.USER_ALREADY_VERIFIED);
        }

        String otp = otpGenerator.generate();
        otpService.issueOtp(email, otp);

        publishOtpResent(user, otp, Instant.now());

        return ResendOtpResponse.from(email);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String identifier = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        log.info("LOGIN_ATTEMPT identifier={} ip={}", MaskingLogArg.email(identifier), MaskingLogArg.ip(ip));

        User user = userRepository.findByEmail(identifier).orElse(null);
        if (user == null) {
            user = userRepository.findByUsername(identifier).orElse(null);
        }

        loginAttemptService.validateIpNotBlocked(ip);

        boolean userFound = user != null;
        boolean passwordMatched = false;

        if (userFound) {
            loginAttemptService.validateNotLocked(user.getId());

            if (user.isLocal() && user.getPasswordHash() != null) {
                passwordMatched = passwordHasher.matches(request.password(), user.getPasswordHash());
            } else {
                passwordMatched = false;
                passwordHasher.matches(request.password(), DUMMY_HASH);
            }
        } else {
            passwordHasher.matches(request.password(), DUMMY_HASH);
        }

        if (!userFound || !passwordMatched) {
            if (userFound) {
                loginAttemptService.recordFailure(user.getId());
            }
            loginAttemptService.recordIpFailure(ip);
            captchaVerifier.recordFailure(CaptchaContext.login(identifier));
            log.warn("LOGIN_FAILED_CREDENTIALS identifier={} userFound={} ip={}",
                    MaskingLogArg.email(identifier), userFound, MaskingLogArg.ip(ip));
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }

        loginAttemptService.clearFailures(user.getId());
        loginAttemptService.clearIpFailures(ip);
        return completeSuccessfulLogin(user, ip, userAgent);
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request, String ip, String userAgent) {
        log.info("GOOGLE_LOGIN_ATTEMPT ip={}", MaskingLogArg.ip(ip));
        loginAttemptService.validateIpNotBlocked(ip);
        GoogleUserInfo info = googleOAuthService.verify(request.idToken(), request.nonce());
        String email = info.email().toLowerCase(Locale.ROOT);

        User user = userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, info.googleSubId()).orElse(null);

        if (user == null) {
            User byEmail = userRepository.findByEmail(email).orElse(null);
            if (byEmail == null) {
                user = createOAuthAccount(info, email);
                log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE action=created", user.getId());
            } else {
                log.warn("OAUTH_EMAIL_CONFLICT email={} existingProvider={} existingStatus={}",
                        MaskingLogArg.email(email), byEmail.getOauthProvider(), byEmail.getStatus());
                throw new BusinessException(IamErrorCode.OAUTH_EMAIL_CONFLICT);
            }
        } else {
            user.linkOAuth(info.googleSubId(), info.fullName(), info.avatarUrl());
            userRepository.save(user);
            log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE action=existing_oauth_user", user.getId());
        }

        loginAttemptService.validateNotLocked(user.getId());
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }

        return completeSuccessfulLogin(user, ip, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshResponse refresh(String expiredAccessToken, String oldRefreshToken, String ip, String userAgent) {
        log.info("TOKEN_REFRESH_REQUEST ip={}", MaskingLogArg.ip(ip));
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        UUID userId = resolveUserIdForRefresh(expiredAccessToken, oldRefreshToken);

        loginAttemptService.validateNotLocked(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN));
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }

        RotationResult result = sessionService.rotate(oldRefreshToken, userId);
        if (result.status() == RotationStatus.NOT_FOUND) {
            log.warn("REFRESH_FAILED_EXPIRED ip={} token={}", MaskingLogArg.ip(ip), MaskingLogArg.token(oldRefreshToken));
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }

        String accessToken = jwtSigner.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().getCode());
        String accessSignature;
        try {
            accessSignature = jwtSigner.extractTokenFingerprint(accessToken);
        } catch (JwtException | IllegalArgumentException ex) {
            accessSignature = null;
        }
        Instant accessExpiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        long refreshMaxAge = result.cookieUpdateRequired()
                ? jwtProperties.getRefreshTokenTtlSeconds()
                : 0L;

        if (result.cookieUpdateRequired() && result.newRefreshToken() != null) {
            sessionService.updateSessionSignature(result.newRefreshToken(), accessSignature);
        }

        return new RefreshResponse(
                accessToken,
                jwtProperties.getAccessTokenTtlSeconds(),
                accessExpiresAt,
                result.newRefreshToken(),
                refreshMaxAge);
    }

    private UUID resolveUserIdForRefresh(String expiredAccessToken, String oldRefreshToken) {
        if (expiredAccessToken != null && !expiredAccessToken.isBlank()) {
            try {
                return jwtSigner.parseExpiredTokenUserId(expiredAccessToken);
            } catch (JwtException ex) {
                throw new BusinessException(IamErrorCode.JWT_EXPIRED);
            }
        }
        String userIdStr = sessionService.findUserIdForRefreshToken(oldRefreshToken);
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Override
    public void logout(String accessToken, String refreshToken, String ip) {
        if ((accessToken == null || accessToken.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
            log.warn("LOGOUT_MISSING_TOKENS ip={}", MaskingLogArg.ip(ip));
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS,
                    "No credentials provided for logout; provide access token or refresh cookie");
        }

        log.info("LOGOUT_REQUEST_RECEIVED ip={} hasAccessToken={} hasRefreshToken={}",
                MaskingLogArg.ip(ip),
                accessToken != null && !accessToken.isBlank(),
                refreshToken != null && !refreshToken.isBlank());

        blacklistAccessTokenIfPresent(accessToken, ip);

        if (refreshToken != null && !refreshToken.isBlank()) {
            UUID ownerUserId = resolveRefreshTokenOwner(refreshToken);
            if (ownerUserId == null) {
                log.info("LOGOUT_NO_ACTIVE_REFRESH ip={}", MaskingLogArg.ip(ip));
            } else {
                try {
                    sessionService.revokeSingleSession(ownerUserId, refreshToken);
                } catch (BusinessException ex) {
                    log.warn("LOGOUT_REVOKE_OWNERSHIP_MISMATCH ip={} ownerUserId={}",
                            MaskingLogArg.ip(ip), ownerUserId);
                    throw ex;
                } catch (Exception ex) {
                    log.warn("LOGOUT_REDIS_ERROR ip={} operation=revokeRefreshToken error={}",
                            MaskingLogArg.ip(ip), ex.getMessage());
                }
            }
        }
    }

    private UUID resolveRefreshTokenOwner(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String ownerStr = sessionService.findUserIdForRefreshToken(refreshToken);
        if (ownerStr == null || ownerStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void blacklistAccessTokenIfPresent(String accessToken, String ip) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String signature;
        long expiryEpochSecond;
        try {
            signature = jwtSigner.extractTokenFingerprint(accessToken);
            expiryEpochSecond = jwtSigner.extractExpiryEpochSecond(accessToken);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("LOGOUT_INVALID_ACCESS_TOKEN ip={} error={}", MaskingLogArg.ip(ip), ex.getMessage());
            return;
        }
        if (signature == null || signature.isBlank()) {
            return;
        }

        long now = Instant.now().getEpochSecond();
        long ttl = (expiryEpochSecond - now) + jwtProperties.getBlacklistClockSkewBufferSeconds();
        if (ttl <= 0) {
            log.info("LOGOUT_BLACKLIST_SKIPPED reason=token_expired signaturePrefix={}",
                    signature.substring(0, Math.min(8, signature.length())));
            return;
        }

        try {
            sessionService.blacklistAccessToken(signature, ttl);
            log.info("TOKENS_INVALIDATED signaturePrefix={} blacklistTTLSeconds={}",
                    signature.substring(0, Math.min(8, signature.length())), ttl);
        } catch (Exception ex) {
            log.warn("LOGOUT_REDIS_ERROR ip={} operation=blacklistAccessToken error={}",
                    MaskingLogArg.ip(ip), ex.getMessage());
        }
    }

    private LoginResponse completeSuccessfulLogin(User user, String ip, String userAgent) {
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_DELETION) {
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }

        IssuedSession issued = sessionService.grantInitialSession(user.getId());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String roleCode = user.getRole() == null ? RoleType.USER.code() : user.getRole().getCode();
        String accessToken = jwtSigner.generateAccessToken(user.getId(), user.getEmail(), roleCode);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

        String accessSignature;
        try {
            accessSignature = jwtSigner.extractTokenFingerprint(accessToken);
        } catch (JwtException | IllegalArgumentException ex) {
            accessSignature = null;
        }
        String resolvedLocation = GeoIpConfig.resolve(geoIpDatabaseReader, ip).display();
        try {
            sessionService.writeSessionMetadata(
                    user.getId(),
                    issued.refreshToken(),
                    accessSignature,
                    ip,
                    userAgent,
                    resolvedLocation);
        } catch (Exception ex) {
            log.warn("WRITE_SESSION_METADATA_FAILED userId={} error={}", user.getId(), ex.getMessage());
        }

        UserInfo info = userMapper.toUserInfo(user);

        eventPublisher.publishEvent(new LoginSuccessEvent(
                user.getId(), user.getEmail(), ip, userAgent, Instant.now()));

        log.info("LOGIN_SUCCESS userId={} role={} status={}",
                user.getId(), roleCode, user.getStatus());

        return new LoginResponse(
                accessToken,
                jwtProperties.getAccessTokenTtlSeconds(),
                expiresAt,
                info,
                issued.refreshToken(),
                jwtProperties.getRefreshTokenTtlSeconds(),
                null,
                null);
    }

    private User createOAuthAccount(GoogleUserInfo info, String email) {
        Role userRole = roleRepository.findByCode(RoleType.USER.code())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND,
                        "Default USER role is missing"));
        User user = User.newOAuthActive(email, info.fullName(), info.googleSubId(), info.avatarUrl(), userRole);
        user = saveUserWithUniqueUsername(user, email);
        log.debug("Created OAuth user with auto-username={}", user.getUsername());
        return user;
    }

    private User saveUserWithUniqueUsername(User user, String email) {
        for (int attempt = 1; attempt <= USERNAME_GENERATION_MAX_ATTEMPTS; attempt++) {
            try {
                return userRepository.save(user);
            } catch (DataIntegrityViolationException ex) {
                log.warn("USERNAME_COLLISION_DETECTED email={} attempt={}/{} username={}",
                        MaskingLogArg.email(email), attempt, USERNAME_GENERATION_MAX_ATTEMPTS, user.getUsername());
                user.setUsername(User.generateUsernamePrefix(email) + User.generateUsernameSuffix());
            }
        }
        log.error("USERNAME_GENERATION_EXHAUSTED email={} attempts={}",
                MaskingLogArg.email(email), USERNAME_GENERATION_MAX_ATTEMPTS);
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                "Failed to generate unique username after " + USERNAME_GENERATION_MAX_ATTEMPTS + " attempts");
    }

    @Override
    public String blacklistAccessTokenSignature(String accessToken) {
        return blacklistAccessTokenFingerprint(accessToken);
    }

    private String blacklistAccessTokenFingerprint(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return jwtSigner.extractTokenFingerprint(accessToken);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private void publishUserRegistered(User user, String otp, Instant issuedAt) {
        UserRegisteredEvent payload = new UserRegisteredEvent(
                user.getId(), user.getEmail(), user.getFullName(), otp, issuedAt);
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.USER_REGISTERED,
                user.getId().toString(),
                payload);
    }

    private void publishUserVerified(User user, Instant verifiedAt) {
        UserVerifiedEvent payload = new UserVerifiedEvent(user.getId(), user.getEmail(), verifiedAt);
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.USER_VERIFIED,
                user.getId().toString(),
                payload);
    }

    private void publishOtpResent(User user, String otp, Instant issuedAt) {
        OtpResentEvent payload = new OtpResentEvent(
                user.getId(), user.getEmail(), user.getFullName(), otp, issuedAt);
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.OTP_RESENT,
                user.getId().toString(),
                payload);
    }
}
