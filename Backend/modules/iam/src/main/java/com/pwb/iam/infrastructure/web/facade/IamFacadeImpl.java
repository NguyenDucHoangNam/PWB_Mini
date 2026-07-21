package com.pwb.iam.infrastructure.web.facade;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.iam.api.IamFacade;
import com.pwb.backend.web.MessageResolver;
import com.pwb.iam.api.OtpService;
import com.pwb.iam.api.dto.GoogleIdTokenPayload;
import com.pwb.iam.api.dto.request.ChangePasswordRequest;
import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.ForgotPasswordRequest;
import com.pwb.iam.api.dto.request.GoogleLoginRequest;
import com.pwb.iam.api.dto.request.LoginRequest;
import com.pwb.iam.api.dto.request.RefreshTokenRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.ResetPasswordRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.api.dto.response.OtpPolicyResult;
import com.pwb.iam.api.dto.response.OtpVerificationOutcome;
import com.pwb.iam.core.model.EmailAddress;
import com.pwb.iam.core.model.OtpPurpose;
import com.pwb.iam.core.model.Password;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.core.model.User;
import com.pwb.iam.core.model.UserStatus;
import com.pwb.iam.core.events.AuthSuccessEvent;
import com.pwb.iam.core.service.PasswordPolicyResult;
import com.pwb.iam.core.service.PasswordPolicyService;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.security.config.PasswordResetProperties;
import com.pwb.iam.infrastructure.security.config.RefreshTokenProperties;
import com.pwb.iam.infrastructure.security.event.AuthEventPublisher;
import com.pwb.iam.infrastructure.security.jwt.GoogleTokenVerifier;
import com.pwb.iam.infrastructure.security.jwt.JwtTokenProvider;
import com.pwb.iam.infrastructure.security.service.LoginAttemptService;
import com.pwb.iam.infrastructure.security.service.RefreshTokenStore;
import com.pwb.iam.infrastructure.security.util.ClientIpResolver;
import com.pwb.iam.infrastructure.service.AuthSupportService;
import com.pwb.iam.infrastructure.service.PasswordResetTokenService;
import com.pwb.iam.infrastructure.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class IamFacadeImpl implements IamFacade {

    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;
    private static final String PROVISIONAL_USERNAME_PREFIX = "user_";

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_SENT";
    private static final String MSG_PASSWORD_RESET = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED_SUCCESSFUL";
    private static final String MSG_LOGOUT = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_OTP_COOLDOWN = "AUTH_OTP_COOLDOWN";

    private final UserJpaRepository userJpaRepository;
    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleLookupService roleLookupService;
    private final AuthSupportService authSupportService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthEventPublisher authEventPublisher;
    private final PasswordResetProperties passwordResetProperties;
    private final OtpService otpService;
    private final OtpProperties otpProperties;
    private final MessageResolver messageResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final RefreshTokenProperties refreshTokenProperties;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordPolicyService passwordPolicyService;
    private final ApplicationEventPublisher eventPublisher;

    @Nullable
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    public IamFacadeImpl(
            UserJpaRepository userJpaRepository,
            PasswordResetTokenJpaRepository passwordResetTokenJpaRepository,
            RoleJpaRepository roleJpaRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            RoleLookupService roleLookupService,
            AuthSupportService authSupportService,
            GoogleTokenVerifier googleTokenVerifier,
            AuthEventPublisher authEventPublisher,
            PasswordResetProperties passwordResetProperties,
            OtpService otpService,
            OtpProperties otpProperties,
            MessageResolver messageResolver,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenStore refreshTokenStore,
            RefreshTokenProperties refreshTokenProperties,
            LoginAttemptService loginAttemptService,
            PasswordResetTokenService passwordResetTokenService,
            PasswordPolicyService passwordPolicyService,
            ApplicationEventPublisher eventPublisher,
            @Nullable StringRedisTemplate stringRedisTemplate) {
        this.userJpaRepository = userJpaRepository;
        this.passwordResetTokenJpaRepository = passwordResetTokenJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.roleLookupService = roleLookupService;
        this.authSupportService = authSupportService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.authEventPublisher = authEventPublisher;
        this.passwordResetProperties = passwordResetProperties;
        this.otpService = otpService;
        this.otpProperties = otpProperties;
        this.messageResolver = messageResolver;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenProperties = refreshTokenProperties;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.passwordPolicyService = passwordPolicyService;
        this.eventPublisher = eventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public AuthMessageResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        Optional<UserJpaEntity> existingOpt = userJpaRepository.findByEmailAndDeletedFalse(email);
        if (existingOpt.isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AUTH);
        }

        String username = generateProvisionalUsername();
        String hashed = passwordEncoder.encode(request.getPassword());

        User domain = User.createLocal(
                username,
                EmailAddress.of(email),
                Password.fromHash(hashed),
                username);

        domain.assignRole(roleLookupService.requireRole(RoleName.USER));

        UserJpaEntity entity = userMapper.toEntity(domain);
        entity.setRole(roleLookupService.requireRoleEntity(RoleName.USER));

        UserJpaEntity saved = userJpaRepository.save(entity);
        User savedDomain = userMapper.toDomain(saved);

        log.info("User registered pending verification: userId={} email={}",
                savedDomain.getUserId(), savedDomain.getEmail().value());

        OtpPolicyResult policy = otpService.requestOtp(
                savedDomain.getEmail().value(), OtpPurpose.REGISTER);
        if (!policy.allowed()) {
            long seconds = policy.cooldownRemaining().toSeconds();
            log.warn("OTP throttled right after register: userId={} cooldown={}s",
                    savedDomain.getUserId(), seconds);
            throw new BusinessException(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED, seconds);
        }

        return AuthMessageResponse.of(
                savedDomain.getUserId(),
                messageResolver.get(MSG_REGISTER));
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        OtpVerificationOutcome outcome = otpService.verifyOtpByUserId(
                request.getUserId(), OtpPurpose.REGISTER, request.getCode());

        switch (outcome.outcome()) {
            case INVALID -> throw new BusinessException(ErrorCode.AUTH_OTP_INVALID);
            case EXPIRED_OR_MISSING -> throw new BusinessException(ErrorCode.AUTH_OTP_EXPIRED);
            case LOCKED -> throw new BusinessException(ErrorCode.AUTH_OTP_LOCKED);
            case OK -> { }
        }

        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = userMapper.toDomain(entity);

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.warn("User already verified or invalid status: userId={} status={}",
                    user.getUserId(), user.getStatus());
        }
        user.markActive();
        UserJpaEntity toSave = userMapper.toEntity(user, entity);
        UserJpaEntity saved = userJpaRepository.save(toSave);

        authEventPublisher.publishUserVerifiedEmail(saved.getId(), saved.getEmail());

        log.info("User OTP verified: userId={}", saved.getId());
        AuthResponse.NextStep nextStep = isProvisionalUsername(saved.getUsername())
                ? AuthResponse.NextStep.COMPLETE_PROFILE
                : AuthResponse.NextStep.NONE;
        return buildAuthResponseWithRotation(
                userMapper.toDomain(saved), nextStep);
    }

    @Override
    @Transactional
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RoleJpaEntity originalRole = entity.getRole();
        User user = userMapper.toDomain(entity);

        String newUsername = request.getUsername().trim();
        if (!newUsername.equals(user.getUsername())
                && userJpaRepository.existsByUsernameAndDeletedFalse(newUsername)) {
            throw new BusinessException(ErrorCode.USER_NAME_EXISTS);
        }
        user.changeUsername(newUsername);
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.changeFullName(request.getFullName().trim());
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            enforcePasswordPolicy(request.getNewPassword());
            user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        }

        UserJpaEntity toSave = userMapper.toEntity(user);
        toSave.setRole(originalRole);
        UserJpaEntity saved = userJpaRepository.save(toSave);
        log.info("Profile completed: userId={} username={}",
                saved.getId(), saved.getUsername());
        return buildAuthResponseWithRotation(
                userMapper.toDomain(saved), AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        String clientIp = ClientIpResolver.getClientIp();

        if (loginAttemptService.isEmailLocked(email)) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }
        if (loginAttemptService.isIpLocked(clientIp)) {
            throw new BusinessException(ErrorCode.AUTH_IP_LOCKED);
        }

        UserJpaEntity entity = userJpaRepository.findByEmailAndDeletedFalse(email).orElse(null);
        if (entity == null) {
            loginAttemptService.recordFailure(email, clientIp);
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        User user = userMapper.toDomain(entity);

        if (user.getStatus() != UserStatus.ACTIVE) {
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
            }
            loginAttemptService.recordFailure(email, clientIp);
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword().getHash())) {
            loginAttemptService.recordFailure(email, clientIp);
            if (loginAttemptService.isEmailLocked(email)) {
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
            }
            if (loginAttemptService.isIpLocked(clientIp)) {
                throw new BusinessException(ErrorCode.AUTH_IP_LOCKED);
            }
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        loginAttemptService.recordSuccess(email, clientIp);
        authEventPublisher.publishLoginSuccess(user.getUserId(), user.getEmail().value());
        log.info("User login success: userId={}", user.getUserId());
        return buildAuthResponseWithRotation(user, AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdTokenPayload payload = googleTokenVerifier.verify(request.getIdToken());

        UserJpaEntity entity = userJpaRepository
                .findByOauthProviderAndOauthIdAndDeletedFalse(
                        com.pwb.iam.core.model.OAuthProvider.GOOGLE, payload.sub())
                .orElse(null);

        User user;
        if (entity == null) {
            Optional<UserJpaEntity> byEmail = userJpaRepository.findByEmailAndDeletedFalse(payload.email());
            if (byEmail.isPresent()) {
                user = userMapper.toDomain(byEmail.get());
                user.linkOAuth(com.pwb.iam.core.model.OAuthProvider.GOOGLE, payload.sub());
                if (payload.picture() != null && !payload.picture().isBlank()
                        && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
                    user.changeAvatarUrl(payload.picture());
                }
                if (payload.name() != null && !payload.name().isBlank()
                        && (user.getFullName() == null || user.getFullName().isBlank())) {
                    user.changeFullName(payload.name());
                }
                if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                    user.markActive();
                }
                authEventPublisher.publishUserLinkedGoogle(
                        user.getUserId(), user.getEmail().value(), user.getFullName());
            } else {
                String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                User fresh = User.createGoogle(
                        username,
                        EmailAddress.of(payload.email()),
                        payload.sub(),
                        payload.name(),
                        payload.picture());
                fresh.assignRole(roleLookupService.requireRole(RoleName.USER));
                fresh.markActive();
                UserJpaEntity freshEntity = userMapper.toEntity(fresh);
                RoleJpaEntity roleEntity = roleJpaRepository.findByNameAndDeletedFalse(RoleName.USER.name())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SEEDER_ROLE_NOT_FOUND));
                freshEntity.setRole(roleEntity);
                UserJpaEntity saved = userJpaRepository.save(freshEntity);
                user = userMapper.toDomain(saved);
                authEventPublisher.publishUserRegisteredGoogle(
                        user.getUserId(), user.getEmail().value(), user.getFullName());
            }
        } else {
            user = userMapper.toDomain(entity);
            RoleJpaEntity originalRole = entity.getRole();
            boolean dirty = false;
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.markActive();
                dirty = true;
            }
            if (payload.picture() != null && !payload.picture().isBlank()
                    && (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())) {
                user.changeAvatarUrl(payload.picture());
                dirty = true;
            }
            if (dirty) {
                UserJpaEntity updatedEntity = userMapper.toEntity(user);
                updatedEntity.setRole(originalRole);
                UserJpaEntity saved = userJpaRepository.save(updatedEntity);
                user = userMapper.toDomain(saved);
            }
        }

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        authEventPublisher.publishLoginSuccess(user.getUserId(), user.getEmail().value());
        log.info("Google login success: userId={} email={}", user.getUserId(), user.getEmail().value());

        AuthResponse.NextStep nextStep = user.getUsername() != null && user.getUsername().startsWith("user_")
                ? AuthResponse.NextStep.COMPLETE_PROFILE
                : AuthResponse.NextStep.NONE;

        return buildAuthResponseWithRotation(user, nextStep);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        String token = request.getRefreshToken();

        if (!authSupportService.validateRefreshToken(token)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String jti;
        try {
            jti = jwtTokenProvider.extractJtiFromRefreshToken(token);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (jti == null || jti.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        UUID userId = refreshTokenStore.findUserId(jti).orElse(null);
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = userMapper.toDomain(entity);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        refreshTokenStore.revoke(jti);

        log.info("Token refreshed: userId={}", user.getUserId());
        return buildAuthResponseWithRotation(user, AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthMessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        enforceResetCooldown(email);

        String sentMessage = messageResolver.get(MSG_FORGOT_PASSWORD);
        Optional<UserJpaEntity> userOpt = userJpaRepository.findByEmailAndDeletedFalse(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email (silent)");
            return AuthMessageResponse.of(null, sentMessage);
        }
        User user = userMapper.toDomain(userOpt.get());

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset skipped for non-active user: userId={} status={}",
                    user.getUserId(), user.getStatus());
            return AuthMessageResponse.of(null, sentMessage);
        }
        if (user.getOauthProvider() != com.pwb.iam.core.model.OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        String signedToken = passwordResetTokenService.generateSignedToken();
        String rawToken = passwordResetTokenService.extractRawToken(signedToken);
        String tokenHash = passwordResetTokenService.hashForStorage(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(passwordResetProperties.getTokenTtlMinutes()));

        passwordResetTokenJpaRepository.invalidateAllForUser(user.getUserId(), now);
        passwordResetTokenJpaRepository.save(PasswordResetTokenJpaEntity.builder()
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .used(false)
                .build());

        String resetLink = passwordResetTokenService.buildResetLink(rawToken);
        authEventPublisher.publishPasswordResetRequested(
                user.getUserId(), user.getEmail().value(), resetLink,
                passwordResetProperties.getTokenTtlMinutes());

        log.info("Password reset requested: userId={} email={}",
                user.getUserId(), user.getEmail().value());
        return AuthMessageResponse.of(user.getUserId(), sentMessage);
    }

    @Override
    @Transactional
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        if (!passwordResetTokenService.verifySignature(request.getToken())) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String rawToken = passwordResetTokenService.extractRawToken(request.getToken());
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String tokenHash = passwordResetTokenService.hashForStorage(rawToken);
        Instant now = Instant.now();

        PasswordResetTokenJpaEntity tokenEntity = passwordResetTokenJpaRepository
                .findActiveByHash(tokenHash, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(tokenEntity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RoleJpaEntity originalRole = entity.getRole();
        User user = userMapper.toDomain(entity);

        if (user.getOauthProvider() != com.pwb.iam.core.model.OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        enforcePasswordPolicy(request.getNewPassword());

        user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        UserJpaEntity toSave = userMapper.toEntity(user);
        toSave.setRole(originalRole);
        UserJpaEntity savedUser = userJpaRepository.save(toSave);

        tokenEntity.markUsed(now);
        passwordResetTokenJpaRepository.save(tokenEntity);

        refreshTokenStore.revokeAllForUser(savedUser.getId());

        authEventPublisher.publishPasswordChanged(savedUser.getId(), savedUser.getEmail());

        log.info("Password reset completed: userId={}", savedUser.getId());
        return AuthMessageResponse.of(savedUser.getId(), messageResolver.get(MSG_PASSWORD_RESET));
    }

    @Override
    @Transactional
    public AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RoleJpaEntity originalRole = entity.getRole();
        User user = userMapper.toDomain(entity);

        if (user.getOauthProvider() != com.pwb.iam.core.model.OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword().getHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword().getHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_REUSED);
        }

        enforcePasswordPolicy(request.getNewPassword());

        user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        UserJpaEntity toSave = userMapper.toEntity(user);
        toSave.setRole(originalRole);
        UserJpaEntity saved = userJpaRepository.save(toSave);
        refreshTokenStore.revokeAllForUser(saved.getId());
        authEventPublisher.publishPasswordChanged(saved.getId(), saved.getEmail());

        log.info("Password changed: userId={}", saved.getId());
        return AuthMessageResponse.of(saved.getId(), messageResolver.get(MSG_PASSWORD_CHANGED));
    }

    @Override
    @Transactional
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        OtpPolicyResult policy = otpService.requestOtp(entity.getEmail(), request.getPurpose());
        if (policy.allowed()) {
            return AuthMessageResponse.of(
                    entity.getId(),
                    messageResolver.get(MSG_OTP_RESENT),
                    (int) otpProperties.getTtlSeconds());
        }
        long seconds = policy.cooldownRemaining().toSeconds();
        return AuthMessageResponse.of(
                entity.getId(),
                messageResolver.get(MSG_OTP_COOLDOWN, seconds),
                (int) seconds);
    }

    @Override
    @Transactional
    public AuthMessageResponse logout(UUID userId) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        refreshTokenStore.revokeAllForUser(entity.getId());
        authEventPublisher.publishLogout(entity.getId(), entity.getEmail());
        log.info("User logged out: userId={}", entity.getId());
        return AuthMessageResponse.of(entity.getId(), messageResolver.get(MSG_LOGOUT));
    }

    private String normalizeEmail(String raw) {
        return raw.trim().toLowerCase();
    }

    private AuthResponse buildAuthResponseWithRotation(User user, AuthResponse.NextStep nextStep) {
        AuthResponse response = authSupportService.buildAuthResponse(user, nextStep);
        String refreshToken = response.getRefreshToken();
        eventPublisher.publishEvent(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value(), refreshToken));
        return response;
    }

    private String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return PROVISIONAL_USERNAME_PREFIX + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
    }

    private boolean isProvisionalUsername(String username) {
        return username != null && username.startsWith(PROVISIONAL_USERNAME_PREFIX);
    }

    private void enforceResetCooldown(String email) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = COOLDOWN_PREFIX + email;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", Duration.ofSeconds(passwordResetProperties.getCooldownSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_COOLDOWN);
        }
    }

    private void enforcePasswordPolicy(String rawPassword) {
        PasswordPolicyResult result = passwordPolicyService.validate(rawPassword);
        if (result.isInvalid()) {
            String reasons = result.violations().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            log.warn("Password policy rejected: violations={}", reasons);
            throw new BusinessException(ErrorCode.WEAK_PASSWORD, reasons);
        }
    }
}
