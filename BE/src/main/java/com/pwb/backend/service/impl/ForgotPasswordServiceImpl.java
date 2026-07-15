package com.pwb.backend.service.impl;

import com.pwb.backend.auth.PasswordResetProperties;
import com.pwb.backend.dto.request.ResetPasswordRequest;
import com.pwb.backend.dto.response.AuthMessageResponse;
import com.pwb.backend.entity.rdbms.PasswordResetToken;
import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.enums.UserStatus;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.repository.rdbms.PasswordResetTokenRepository;
import com.pwb.backend.repository.rdbms.UserRepository;
import com.pwb.backend.service.AuthEventPublisher;
import com.pwb.backend.service.ForgotPasswordService;
import com.pwb.backend.utils.helper.MessageHelper;
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
public class ForgotPasswordServiceImpl implements ForgotPasswordService {

    private static final String COOLDOWN_PREFIX = "password-reset:cooldown:";
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher authEventPublisher;
    private final MessageHelper messageHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String MSG_REQUESTED = "auth.password_reset.email_sent";
    private static final String MSG_SUCCESS = "auth.password_reset.success";

    @Override
    @Transactional
    public AuthMessageResponse requestReset(String emailRaw) {
        String email = emailRaw.trim().toLowerCase();

        enforceCooldown(email);

        Optional<User> userOpt = userRepository.findByEmailAndDeletedFalse(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email (silent)");
            return AuthMessageResponse.of(null, messageHelper.get(MSG_REQUESTED));
        }

        User user = userOpt.get();

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset skipped for non-active user: userId={} status={}", user.getId(), user.getStatus());
            return AuthMessageResponse.of(null, messageHelper.get(MSG_REQUESTED));
        }

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        String rawToken = generateSecureToken();
        String tokenHash = sha256(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(passwordResetProperties.getTokenTtlMinutes()));

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .used(false)
                .build();
        passwordResetTokenRepository.save(token);

        passwordResetTokenRepository.invalidateAllForUser(user.getId(), now);

        String resetLink = buildResetLink(rawToken);

        authEventPublisher.publishPasswordResetRequested(
                user.getId(), user.getEmail(), resetLink, passwordResetProperties.getTokenTtlMinutes());

        log.info("Password reset requested: userId={} email={}", user.getId(), user.getEmail());
        return AuthMessageResponse.of(user.getId(), messageHelper.get(MSG_REQUESTED));
    }

    @Override
    @Transactional
    public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.getToken());

        PasswordResetToken token = passwordResetTokenRepository
                .findActiveByHash(tokenHash, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        User user = userRepository.findByIdAndDeletedFalse(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_USER_NO_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.markUsed(Instant.now());
        passwordResetTokenRepository.save(token);

        authEventPublisher.publishPasswordChanged(user.getId(), user.getEmail());

        log.info("Password reset completed: userId={}", user.getId());
        return AuthMessageResponse.of(user.getId(), messageHelper.get(MSG_SUCCESS));
    }

    private void enforceCooldown(String email) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = COOLDOWN_PREFIX + email;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", Duration.ofSeconds(passwordResetProperties.getCooldownSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            Long ttl = stringRedisTemplate.getExpire(key);
            long seconds = (ttl == null || ttl <= 0) ? passwordResetProperties.getCooldownSeconds() : ttl;
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_RESET_COOLDOWN, seconds);
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