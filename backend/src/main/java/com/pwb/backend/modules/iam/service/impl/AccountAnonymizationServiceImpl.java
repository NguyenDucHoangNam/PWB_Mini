package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.common.outbox.OutboxService;
import com.pwb.backend.common.outbox.event.AccountAnonymizedEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.AccountAnonymizationService;
import com.pwb.backend.modules.iam.service.AnonymizationReport;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import com.pwb.backend.modules.iam.service.LoginAttemptService;
import com.pwb.backend.modules.iam.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountAnonymizationServiceImpl implements AccountAnonymizationService {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final AvatarUploadService avatarUploadService;
    private final OutboxService outboxService;

    @Value("${app.iam.account-anonymization.grace-days}")
    private int graceDays;

    @Override
    public AnonymizationReport runOnce(int batchSize) {
        int safeBatch = Math.max(batchSize, 1);
        Instant start = Instant.now();
        Instant threshold = start.minus(Duration.ofDays(Math.max(graceDays, 1)));

        List<User> expired = userRepository.findExpiredPendingDeletion(
                threshold, PageRequest.of(0, safeBatch));
        if (expired.isEmpty()) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            log.info("ANONYMIZATION_BATCH_EMPTY threshold={} batchSize={}", threshold, safeBatch);
            return new AnonymizationReport(0, durationMs, AnonymizationReport.STATUS_EMPTY);
        }

        int processed = 0;
        for (User user : expired) {
            UUID userId = user.getId();
            try {
                anonymizeSingle(userId);
                processed += 1;
                log.info("USER_ANONYMIZED_SUCCESS userId={}", userId);
            } catch (Exception ex) {
                log.error("USER_ANONYMIZATION_FAILED userId={} error={}",
                        userId, ex.getMessage(), ex);
            }
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("ANONYMIZATION_BATCH_PROCESSED requested={} processed={} durationMs={}",
                expired.size(), processed, durationMs);
        return new AnonymizationReport(
                processed,
                durationMs,
                processed > 0 ? AnonymizationReport.STATUS_COMPLETED : AnonymizationReport.STATUS_FAILED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anonymizeSingle(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        if (user.getStatus() != UserStatus.PENDING_DELETION || user.isDeleted()) {
            log.info("USER_ANONYMIZATION_SKIP userId={} status={} deleted={}",
                    userId, user.getStatus(), user.isDeleted());
            return;
        }

        sessionService.purgeUserSessionData(userId);
        loginAttemptService.clearFailures(userId);
        try {
            avatarUploadService.deleteAvatar(userId);
        } catch (Exception ex) {
            log.warn("AVATAR_DELETE_FAILED_ON_ANONYMIZE userId={} error={}", userId, ex.getMessage());
        }

        Instant deletionRequestedAt = user.getDeletionRequestedAt();
        user.anonymize(userId);
        user.softDelete(BaseEntity.SYSTEM_PRINCIPAL);
        userRepository.save(user);

        publishAnonymizedEvent(userId, deletionRequestedAt);
    }

    private void publishAnonymizedEvent(UUID userId, Instant deletionRequestedAt) {
        AccountAnonymizedEvent payload = new AccountAnonymizedEvent(
                userId, Instant.now(), deletionRequestedAt);
        outboxService.publish(
                OutboxEventTypes.AGGREGATE_USER,
                userId,
                OutboxEventTypes.ACCOUNT_ANONYMIZED,
                userId.toString(),
                payload);
    }
}
