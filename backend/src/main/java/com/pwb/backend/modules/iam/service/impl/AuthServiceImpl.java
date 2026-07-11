package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.repository.OutboxEventRepository;
import com.pwb.backend.common.security.JwtProperties;
import com.pwb.backend.common.security.JwtSigner;
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
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(IamErrorCode.EMAIL_ALREADY_EXISTS);
        }
        Role userRole = roleRepository.findByCode(RoleType.USER.code())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND,
                        "Default USER role is missing"));

        User user = User.newPending(
                email,
                passwordHasher.hash(request.password()),
                request.fullName(),
                userRole);

        userRepository.save(user);

        String otp = otpGenerator.generate();
        otpService.issueOtp(email, otp);

        publishUserRegistered(user, otp, Instant.now());

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
        if (user == null && identifier.contains("@")) {
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }
        if (user == null) {
            user = userRepository.findByEmail(identifier).orElse(null);
        }
        if (user == null) {
            log.warn("LOGIN_FAILED_CREDENTIALS identifier={} ip={}", MaskingLogArg.email(identifier), MaskingLogArg.ip(ip));
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }

        loginAttemptService.validateNotLocked(user.getId());

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessException(
                    IamErrorCode.REGISTRATION_IN_PROGRESS,
                    "Account pending OTP verification",
                    null,
                    Map.of("redirectTo", "/verify-otp", "email", maskEmailForRedirect(user.getEmail())));
        }

        if (user.isLocal() && user.getPasswordHash() == null) {
            log.warn("LOGIN_FAILED_CREDENTIALS userId={} reason=oauth_only", user.getId());
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }

        boolean passwordMatched = passwordHasher.matches(request.password(), user.getPasswordHash());
        if (!passwordMatched) {
            loginAttemptService.recordFailure(user.getId());
            log.warn("LOGIN_FAILED_CREDENTIALS userId={} ip={}", user.getId(), MaskingLogArg.ip(ip));
            throw new BusinessException(IamErrorCode.BAD_CREDENTIALS);
        }

        loginAttemptService.clearFailures(user.getId());
        return completeSuccessfulLogin(user, ip, userAgent);
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request, String ip, String userAgent) {
        log.info("GOOGLE_LOGIN_ATTEMPT ip={}", MaskingLogArg.ip(ip));
        GoogleUserInfo info = googleOAuthService.verify(request.idToken());
        String email = info.email().toLowerCase(Locale.ROOT);

        User user = userRepository.findByOauthProviderAndOauthId(OauthProvider.GOOGLE, info.googleSubId()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
        }
        if (user == null) {
            user = createOAuthAccount(info, email);
            log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE action=created", user.getId());
        } else {
            if (user.getStatus() == UserStatus.BANNED) {
                throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
            }
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setPasswordHash(null);
                user.setOauthProvider(OauthProvider.GOOGLE);
                user.setOauthId(info.googleSubId());
                if (info.fullName() != null && !info.fullName().isBlank()) {
                    user.setFullName(info.fullName());
                }
                if (info.avatarUrl() != null && !info.avatarUrl().isBlank()) {
                    user.setAvatarUrl(info.avatarUrl());
                }
                user.setStatus(UserStatus.ACTIVE);
                user.setEmailVerifiedAt(Instant.now());
                clearOtpKeysForEmail(email);
            } else {
                user.setOauthProvider(OauthProvider.GOOGLE);
                user.setOauthId(info.googleSubId());
                if (info.fullName() != null && !info.fullName().isBlank()) {
                    user.setFullName(info.fullName());
                }
                if (info.avatarUrl() != null && !info.avatarUrl().isBlank()) {
                    user.setAvatarUrl(info.avatarUrl());
                }
            }
            userRepository.save(user);
            log.info("GOOGLE_ACCOUNT_LINKED userId={} provider=GOOGLE", user.getId());
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
        if (expiredAccessToken == null || expiredAccessToken.isBlank()) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        }
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_REFRESH_TOKEN);
        }
        UUID userId;
        try {
            userId = jwtSigner.parseExpiredTokenUserId(expiredAccessToken);
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        } catch (JwtException ex) {
            throw new BusinessException(IamErrorCode.JWT_EXPIRED);
        }

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
        Instant accessExpiresAt = Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        long refreshMaxAge = result.cookieUpdateRequired()
                ? jwtProperties.getRefreshTokenTtlSeconds()
                : 0L;

        return new RefreshResponse(
                accessToken,
                jwtProperties.getAccessTokenTtlSeconds(),
                accessExpiresAt,
                result.newRefreshToken(),
                refreshMaxAge);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionService.revokeSingleSession(refreshToken);
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
        String usernamePrefix = User.generateUsernamePrefix(email);
        String username = usernamePrefix + User.generateUsernameSuffix();
        User user = User.newOAuthActive(email, info.fullName(), info.googleSubId(), info.avatarUrl(), userRole);
        userRepository.save(user);
        log.debug("Created OAuth user with auto-username={}", username);
        return user;
    }

    private void clearOtpKeysForEmail(String email) {
        String key = email.toLowerCase(Locale.ROOT);
        stringRedisTemplate.delete("otp:" + key);
        stringRedisTemplate.delete("otp:attempt:" + key);
        stringRedisTemplate.delete("otp:lock:" + key);
    }

    private static String maskEmailForRedirect(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
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
                Instant.now());
        outboxRepository.save(row);

        eventPublisher.publishEvent(new OutboxCreatedEvent(row.getId(), topic, OutboxEventTypes.AGGREGATE_USER));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
