package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.event.PasswordResetRequestedEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.repository.OutboxEventRepository;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.PasswordChangeService;
import com.pwb.backend.modules.iam.service.PasswordResetTokenService;
import com.pwb.backend.modules.iam.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeServiceImpl implements PasswordChangeService {

    private static final int TIMING_FLOOR_MILLIS = 50;
    private static final int TIMING_CEILING_MILLIS = 150;
    private static final int DUMMY_PASSWORD_LENGTH = 16;

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenService resetTokenService;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final OutboxPayloadCipher outboxCipher;

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getOauthProvider() != OauthProvider.LOCAL) {
            applyTimingFlattener();
            log.info("FORGOT_PASSWORD_NO_OP email={} reason={}",
                    MaskingLogArg.email(normalized),
                    user == null ? "unknown_email"
                            : user.getStatus() != UserStatus.ACTIVE ? "inactive_account"
                            : "oauth_account");
            return;
        }

        String token = resetTokenService.issueToken(normalized);
        Instant issuedAt = Instant.now();
        publishPasswordResetEvent(user, token, issuedAt);

        log.info("FORGOT_PASSWORD_REQUESTED userId={} email={}", user.getId(), MaskingLogArg.email(normalized));
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(IamErrorCode.INVALID_RESET_TOKEN);
        }
        String email = resetTokenService.consumeToken(token);
        if (email == null) {
            throw new BusinessException(IamErrorCode.INVALID_RESET_TOKEN);
        }

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(IamErrorCode.INVALID_RESET_TOKEN));
        if (user.getOauthProvider() != OauthProvider.LOCAL || user.getStatus() != UserStatus.ACTIVE) {
            resetTokenService.invalidate(token);
            throw new BusinessException(IamErrorCode.OAUTH_ONLY_ACCOUNT);
        }

        String hashed = passwordHasher.hash(newPassword);
        user.setPasswordHash(hashed);
        userRepository.save(user);

        resetTokenService.invalidate(token);
        loginAttemptService.clearFailures(user.getId());
        int revokedSessions = sessionService.revokeAllSessionsCompletely(user.getId());

        log.info("PASSWORD_RESET_COMPLETED userId={} revokedSessions={}", user.getId(), revokedSessions);
    }

    @Override
    @Transactional
    public int changePassword(UUID userId, String oldPassword, String newPassword, String currentRefreshToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getOauthProvider() != OauthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new BusinessException(IamErrorCode.OAUTH_ONLY_ACCOUNT);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }

        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(userId);
            log.warn("CHANGE_PASSWORD_FAILED userId={} reason=invalid_old_password", userId);
            throw new BusinessException(IamErrorCode.INVALID_OLD_PASSWORD);
        }
        if (passwordHasher.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(IamErrorCode.PASSWORD_REUSE_BLOCKED);
        }

        user.setPasswordHash(passwordHasher.hash(newPassword));
        userRepository.save(user);

        loginAttemptService.clearFailures(userId);
        int revokedSessions = sessionService.revokeAllSessionsExcept(userId, currentRefreshToken);

        log.info("PASSWORD_CHANGED userId={} revokedOtherSessions={}", userId, revokedSessions);
        return revokedSessions;
    }

    private void applyTimingFlattener() {
        passwordHasher.hash(generateDummyPassword());
        int range = TIMING_CEILING_MILLIS - TIMING_FLOOR_MILLIS + 1;
        int delay = ThreadLocalRandom.current().nextInt(range) + TIMING_FLOOR_MILLIS;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateDummyPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&";
        StringBuilder sb = new StringBuilder(DUMMY_PASSWORD_LENGTH);
        for (int i = 0; i < DUMMY_PASSWORD_LENGTH; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void publishPasswordResetEvent(User user, String token, Instant issuedAt) {
        PasswordResetRequestedEvent payload = new PasswordResetRequestedEvent(
                user.getId(), user.getEmail(), user.getFullName(), token, issuedAt);

        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.PASSWORD_RESET,
                user.getId().toString(),
                serialize(payload),
                Instant.now(),
                null,
                1);
        outboxRepository.save(row);

        eventPublisher.publishEvent(new OutboxCreatedEvent(row.getId(), KafkaTopics.IAM_PASSWORD_RESET, OutboxEventTypes.AGGREGATE_USER));
    }

    private String serialize(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return outboxCipher.encrypt(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}