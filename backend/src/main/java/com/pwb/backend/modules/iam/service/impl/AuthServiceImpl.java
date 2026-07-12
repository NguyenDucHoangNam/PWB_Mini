package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.geoip2.DatabaseReader;
import com.pwb.backend.common.config.GeoIpConfig;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.repository.OutboxEventRepository;
import com.pwb.backend.common.security.jwt.JwtProperties;
import com.pwb.backend.common.security.jwt.JwtSigner;
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
import com.pwb.backend.modules.iam.dto.response.VerifyOtpResponse;
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
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.OtpService;
import com.pwb.backend.modules.iam.service.SessionService;
import com.pwb.backend.modules.iam.session.IssuedSession;
import com.pwb.backend.modules.iam.session.RotationResult;
import com.pwb.backend.modules.iam.session.RotationStatus;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final OutboxEventRepository outboxRepository;
    private final OtpService otpService;
    private final PasswordHasher passwordHasher;
    private final SecureRandomOtpGenerator otpGenerator;
    private final UserMapper userMapper;
    private final JwtSigner jwtSigner;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final GoogleOAuthService googleOAuthService;
    private final OutboxPayloadCipher outboxCipher;
    private final StringRedisTemplate stringRedisTemplate;
    private final DatabaseReader geoIpDatabaseReader;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailForUpdate(email).orElse(null);

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
            existing.setPasswordHash(passwordHasher.hash(request.password()));
            if (request.fullName() != null && !request.fullName().isBlank()) {
                existing.setFullName(request.fullName());
            }
            user = userRepository.save(existing);
            isResend = true;
        } else {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String otp = otpGenerator.generate();
        otpService.issueOtp(email, otp);

        publishUserRegistered(user, otp, Instant.now());

        if (isResend) {
            log.info("REGISTRATION_RESENT userId={} email={}", user.getId(), MaskingLogArg.email(email));
        }
        return userMapper.userToRegisterResponse(user);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
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
            throw new BusinessException(
                    IamErrorCode.OTP_LOCKED,
                    "OTP is locked. Retry after " + otpService.lockoutRetryAfterSeconds() + " seconds",
                    null,
                    Map.of("retryAfterSeconds", otpService.lockoutRetryAfterSeconds()));
        }

        boolean ok;
        try {
            ok = otpService.verifyOtp(email, request.otp());
        } catch (BusinessException ex) {
            throw ex;
        }
        if (!ok) {
            throw new BusinessException(IamErrorCode.INVALID_OTP);
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        String roleCode = user.getRole().getCode();
        String accessToken = jwtSigner.generateAccessToken(user.getId(), user.getEmail(), roleCode);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

        return new VerifyOtpResponse(user.getId(), user.getEmail(), roleCode, accessToken, expiresAt);
    }

    @Override
    @Transactional
    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (!otpService.canResend(email)) {
            throw new BusinessException(IamErrorCode.OTP_RESEND_COOLDOWN,
                    "Please wait before requesting a new OTP",
                    null,
                    Map.of("retryAfterSeconds", 60L));
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.USER_ALREADY_VERIFIED);
        }

        String otp = otpGenerator.generate();
        otpService.issueOtp(email, otp);
        otpService.markResent(email);

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
        GoogleUserInfo info = googleOAuthService.verify(request.idToken(), request.nonce());
        String email = info.email().toLowerCase(Locale.ROOT);

        User user = userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, info.googleSubId()).orElse(null);

        if (user == null) {
            User byEmail = userRepository.findByEmail(email).orElse(null);
            if (byEmail == null) {
                user = createOAuthAccount(info, email);
                log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE action=created", user.getId());
            } else if (byEmail.isLocal() && byEmail.getStatus() == UserStatus.PENDING_VERIFICATION) {
                byEmail.linkOAuth(info.googleSubId(), info.fullName(), info.avatarUrl());
                byEmail.activateFromOtp();
                byEmail.setPasswordHash(null);
                clearOtpKeysForEmail(email);
                userRepository.save(byEmail);
                user = byEmail;
                log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE action=claim_pending_local", user.getId());
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
            accessSignature = jwtSigner.extractSignature(accessToken);
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
            return;
        }

        log.info("LOGOUT_REQUEST_RECEIVED ip={} hasAccessToken={} hasRefreshToken={}",
                MaskingLogArg.ip(ip),
                accessToken != null && !accessToken.isBlank(),
                refreshToken != null && !refreshToken.isBlank());

        blacklistAccessTokenIfPresent(accessToken, ip);

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                sessionService.revokeSingleSession(refreshToken);
            } catch (Exception ex) {
                log.warn("LOGOUT_REDIS_ERROR ip={} operation=revokeRefreshToken error={}",
                        MaskingLogArg.ip(ip), ex.getMessage());
            }
        }
    }

    private void blacklistAccessTokenIfPresent(String accessToken, String ip) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String signature;
        long expiryEpochSecond;
        try {
            signature = jwtSigner.extractSignature(accessToken);
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
            accessSignature = jwtSigner.extractSignature(accessToken);
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
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return jwtSigner.extractSignature(accessToken);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private void clearOtpKeysForEmail(String email) {
        String key = email.toLowerCase(Locale.ROOT);
        stringRedisTemplate.delete("otp:" + key);
        stringRedisTemplate.delete("otp:attempt:" + key);
        stringRedisTemplate.delete("otp:lock:" + key);
    }

    private void publishUserRegistered(User user, String otp, Instant issuedAt) {
        publishUserEvent(user, otp, issuedAt, OutboxEventTypes.USER_REGISTERED, KafkaTopics.IAM_USER_REGISTERED);
    }

    private void publishOtpResent(User user, String otp, Instant issuedAt) {
        publishUserEvent(user, otp, issuedAt, OutboxEventTypes.OTP_RESENT, KafkaTopics.IAM_OTP_RESENT);
    }

    private void publishUserEvent(User user, String otp, Instant issuedAt, String eventType, String topic) {
        UserRegisteredEvent payload = new UserRegisteredEvent(
                user.getId(), user.getEmail(), user.getFullName(), otp, issuedAt);

        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                eventType,
                user.getId().toString(),
                serialize(payload),
                Instant.now(),
                null,
                1);
        outboxRepository.save(row);

        eventPublisher.publishEvent(new OutboxCreatedEvent(row.getId(), topic, OutboxEventTypes.AGGREGATE_USER));
    }

    private String serialize(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return outboxCipher.encrypt(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
