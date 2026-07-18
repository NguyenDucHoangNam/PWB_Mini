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
import com.pwb.iam.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.security.config.PasswordResetProperties;
import com.pwb.iam.infrastructure.security.event.AuthEventPublisher;
import com.pwb.iam.infrastructure.security.jwt.GoogleTokenVerifier;
import com.pwb.iam.infrastructure.service.AuthSupportService;
import com.pwb.iam.infrastructure.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final int TOKEN_BYTES = 32;
    private static final int PROVISIONAL_USERNAME_RANDOM_LENGTH = 16;

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_FORGOT_PASSWORD = "AUTH_FORGOT_PASSWORD_SENT";
    private static final String MSG_PASSWORD_RESET = "AUTH_PASSWORD_RESET_SUCCESSFUL";
    private static final String MSG_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED_SUCCESSFUL";
    private static final String MSG_LOGOUT = "AUTH_LOGOUT_SUCCESSFUL";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";
    private static final String MSG_OTP_COOLDOWN = "AUTH_OTP_COOLDOWN";

    private final UserJpaRepository userJpaRepository;
    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleLookupService roleLookupService;
    private final AuthSupportService authSupportService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthEventPublisher authEventPublisher;
    private final PasswordResetProperties passwordResetProperties;
    private final OtpService otpService;
    private final MessageResolver messageResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

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

        UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(domain));
        User savedDomain = userMapper.toDomain(saved);

        log.info("User registered pending verification: userId={} email={}",
                savedDomain.getUserId(), savedDomain.getEmail().value());

        OtpPolicyResult policy = otpService.requestOtp(
                savedDomain.getEmail().value(), OtpPurpose.REGISTER);
        if (!policy.allowed()) {
            log.warn("OTP throttled right after register: userId={} cooldown={}",
                    savedDomain.getUserId(), policy.cooldownRemaining());
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
        UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(user));

        authEventPublisher.publishUserVerifiedEmail(saved.getId(), saved.getEmail());

        log.info("User OTP verified: userId={}", saved.getId());
        return authSupportService.buildAuthResponse(
                userMapper.toDomain(saved), AuthResponse.NextStep.COMPLETE_PROFILE);
    }

    @Override
    @Transactional
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
            user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        }

        UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(user));
        log.info("Profile completed: userId={} username={}",
                saved.getId(), saved.getUsername());
        return authSupportService.buildAuthResponse(
                userMapper.toDomain(saved), AuthResponse.NextStep.NONE);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        UserJpaEntity entity = userJpaRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));
        User user = userMapper.toDomain(entity);

        if (user.getStatus() != UserStatus.ACTIVE) {
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
            }
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword().getHash())) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        authEventPublisher.publishLoginSuccess(user.getUserId(), user.getEmail().value());
        log.info("User login success: userId={}", user.getUserId());
        return authSupportService.buildAuthResponse(user, AuthResponse.NextStep.NONE);
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
                UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(fresh));
                user = userMapper.toDomain(saved);
                authEventPublisher.publishUserRegisteredGoogle(
                        user.getUserId(), user.getEmail().value(), user.getFullName());
            }
        } else {
            user = userMapper.toDomain(entity);
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
                UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(user));
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

        return authSupportService.buildAuthResponse(user, nextStep);
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

        UUID userId = authSupportService.extractUserIdFromRefreshToken(token);
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = userMapper.toDomain(entity);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED);
        }

        log.info("Token refreshed: userId={}", user.getUserId());
        return authSupportService.buildAuthResponse(user, AuthResponse.NextStep.NONE);
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

        String rawToken = generateSecureToken();
        String tokenHash = sha256(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(passwordResetProperties.getTokenTtlMinutes()));

        passwordResetTokenJpaRepository.invalidateAllForUser(user.getUserId(), now);
        passwordResetTokenJpaRepository.save(PasswordResetTokenJpaEntity.builder()
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .used(false)
                .build());

        String resetLink = buildResetLink(rawToken);
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
        String tokenHash = sha256(request.getToken());
        Instant now = Instant.now();

        PasswordResetTokenJpaEntity tokenEntity = passwordResetTokenJpaRepository
                .findActiveByHash(tokenHash, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(tokenEntity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = userMapper.toDomain(entity);

        if (user.getOauthProvider() != com.pwb.iam.core.model.OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        UserJpaEntity savedUser = userJpaRepository.save(userMapper.toEntity(user));

        tokenEntity.markUsed(now);
        passwordResetTokenJpaRepository.save(tokenEntity);

        authEventPublisher.publishPasswordChanged(savedUser.getId(), savedUser.getEmail());

        log.info("Password reset completed: userId={}", savedUser.getId());
        return AuthMessageResponse.of(savedUser.getId(), messageResolver.get(MSG_PASSWORD_RESET));
    }

    @Override
    @Transactional
    public AuthMessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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

        user.changePassword(Password.fromHash(passwordEncoder.encode(request.getNewPassword())));
        UserJpaEntity saved = userJpaRepository.save(userMapper.toEntity(user));
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
            return AuthMessageResponse.of(entity.getId(), messageResolver.get(MSG_OTP_RESENT));
        }
        long seconds = policy.cooldownRemaining().toSeconds();
        return AuthMessageResponse.of(entity.getId(),
                messageResolver.get(MSG_OTP_COOLDOWN, seconds));
    }

    @Override
    @Transactional
    public AuthMessageResponse logout(UUID userId) {
        UserJpaEntity entity = userJpaRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        authEventPublisher.publishLogout(entity.getId(), entity.getEmail());
        log.info("User logged out: userId={}", entity.getId());
        return AuthMessageResponse.of(entity.getId(), messageResolver.get(MSG_LOGOUT));
    }

    private String normalizeEmail(String raw) {
        return raw.trim().toLowerCase();
    }

    private String generateProvisionalUsername() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "user_" + hex.substring(0, PROVISIONAL_USERNAME_RANDOM_LENGTH);
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

    private String buildResetLink(String rawToken) {
        String base = passwordResetProperties.getFrontendUrl();
        String path = passwordResetProperties.getResetPath();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path + "?token=" + rawToken;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
