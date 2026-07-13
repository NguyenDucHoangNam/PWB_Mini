package com.pwb.backend.modules.iam.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.event.AccountDeletionCancelledEvent;
import com.pwb.backend.common.outbox.event.AccountDeletionRequestedEvent;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.repository.OutboxEventRepository;
import com.pwb.backend.common.util.MaskingLogArg;
import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.modules.iam.dto.request.DeleteAccountRequest;
import com.pwb.backend.modules.iam.dto.response.UserProfileResponse;
import com.pwb.backend.modules.iam.enums.OauthProvider;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.mapper.UserMapper;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.AccountDeletionService;
import com.pwb.backend.modules.iam.service.GoogleOAuthService;
import com.pwb.backend.modules.iam.service.GoogleUserInfo;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final GoogleOAuthService googleOAuthService;
    private final OutboxEventRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final OutboxPayloadCipher outboxCipher;
    private final UserMapper userMapper;

    @Value("${app.iam.account-deletion.grace-days}")
    private int graceDays;

    @Override
    @Transactional
    public UserProfileResponse requestDeletion(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(IamErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() == UserStatus.PENDING_DELETION) {
            throw new BusinessException(IamErrorCode.DELETION_ALREADY_REQUESTED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(IamErrorCode.REAUTH_REQUIRED,
                    "Account must be ACTIVE to request deletion");
        }

        reauthenticate(user, request);

        Instant now = Instant.now();
        Instant scheduledPermanent = now.plus(Duration.ofDays(Math.max(graceDays, 1)));

        user.markDeletionRequested(now);
        userRepository.save(user);

        loginAttemptService.clearFailures(userId);
        sessionService.purgeUserSessionData(userId);

        publishDeletionRequested(user, now, scheduledPermanent);

        log.warn("ACCOUNT_DELETION_REQUESTED userId={} email={} scheduledPermanentDeletionAt={} graceDays={}",
                user.getId(), MaskingLogArg.email(user.getEmail()), scheduledPermanent, graceDays);
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse cancelDeletion(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING_DELETION) {
            throw new BusinessException(IamErrorCode.USER_NOT_PENDING_DELETION,
                    "Account is not in PENDING_DELETION state, current status: " + user.getStatus());
        }

        Instant now = Instant.now();
        user.cancelDeletion();
        userRepository.save(user);

        publishDeletionCancelled(user, now);

        log.info("ACCOUNT_DELETION_CANCELLED userId={} email={}", userId, MaskingLogArg.email(user.getEmail()));
        return userMapper.toUserProfileResponse(user);
    }

    private void reauthenticate(User user, DeleteAccountRequest request) {
        if (user.getOauthProvider() == OauthProvider.LOCAL) {
            if (request.password() == null || request.password().isBlank()) {
                throw new BusinessException(IamErrorCode.REAUTH_REQUIRED,
                        "Password is required for LOCAL account re-authentication");
            }
            if (request.idToken() != null && !request.idToken().isBlank()) {
                throw new BusinessException(IamErrorCode.REAUTH_REQUIRED,
                        "Provide EITHER password OR idToken, not both");
            }
            if (user.getPasswordHash() == null) {
                loginAttemptService.recordFailure(user.getId());
                log.warn("DELETION_REAUTH_FAILED userId={} reason=password_hash_missing", user.getId());
                throw new BusinessException(IamErrorCode.INVALID_PASSWORD_REAUTH);
            }
            if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
                loginAttemptService.recordFailure(user.getId());
                log.warn("DELETION_REAUTH_FAILED userId={} reason=invalid_password", user.getId());
                throw new BusinessException(IamErrorCode.INVALID_PASSWORD_REAUTH);
            }
            return;
        }

        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new BusinessException(IamErrorCode.REAUTH_REQUIRED,
                    "idToken is required for OAuth account re-authentication");
        }
        if (request.password() != null && !request.password().isBlank()) {
            throw new BusinessException(IamErrorCode.REAUTH_REQUIRED,
                    "Provide EITHER password OR idToken, not both");
        }
        try {
            GoogleUserInfo info = googleOAuthService.verify(request.idToken());
            String email = info.email() == null ? "" : info.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN,
                        "idToken email does not match account");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DELETION_REAUTH_FAILED userId={} reason=invalid_idToken error={}",
                    user.getId(), ex.getMessage());
            throw new BusinessException(IamErrorCode.INVALID_OAUTH_TOKEN, ex.getMessage());
        }
    }

    private void publishDeletionRequested(User user, Instant deletionRequestedAt, Instant scheduledPermanentDeletionAt) {
        AccountDeletionRequestedEvent payload = new AccountDeletionRequestedEvent(
                user.getId(), user.getEmail(), user.getFullName(),
                deletionRequestedAt, scheduledPermanentDeletionAt, graceDays);

        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.ACCOUNT_DELETION_REQUESTED,
                user.getId().toString(),
                serialize(payload),
                Instant.now(),
                null,
                1);
        outboxRepository.save(row);

        eventPublisher.publishEvent(new OutboxCreatedEvent(
                row.getId(), KafkaTopics.IAM_ACCOUNT_DELETION, OutboxEventTypes.AGGREGATE_USER));
    }

    private void publishDeletionCancelled(User user, Instant reactivatedAt) {
        AccountDeletionCancelledEvent payload = new AccountDeletionCancelledEvent(
                user.getId(), user.getEmail(), user.getFullName(), reactivatedAt);

        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                OutboxEventTypes.AGGREGATE_USER,
                user.getId(),
                OutboxEventTypes.ACCOUNT_DELETION_CANCELLED,
                user.getId().toString(),
                serialize(payload),
                Instant.now(),
                null,
                1);
        outboxRepository.save(row);

        eventPublisher.publishEvent(new OutboxCreatedEvent(
                row.getId(), KafkaTopics.IAM_ACCOUNT_DELETION, OutboxEventTypes.AGGREGATE_USER));
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
