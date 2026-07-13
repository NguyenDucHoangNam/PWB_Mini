package com.pwb.backend.common.outbox.scheduler;

import com.pwb.backend.common.outbox.publisher.OutboxEventSerializer;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.enums.OutboxStatus;
import com.pwb.backend.common.repository.OutboxEventRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class OutboxRetryScheduler {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventSerializer outboxEventSerializer;
    private final BackoffCalculator backoffCalculator;
    private final OutboxRetryResultHandler resultHandler;

    @Value("${app.outbox.max-attempts}")
    private int maxAttempts;
    @Value("${app.outbox.retry-batch-size}")
    private int batchSize;


    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms}")
    @SchedulerLock(name = "outbox-retry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    @Transactional
    public void retryDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> due = outboxRepository.findDueForRetry(
                Set.of(OutboxStatus.PENDING, OutboxStatus.FAILED), now, batchSize);
        if (due.isEmpty()) {
            return;
        }

        for (OutboxEvent row : due) {
            row.setStatus(OutboxStatus.PROCESSING);
            outboxRepository.save(row);

            String topic = resolveTopic(row);
            if (topic == null) {
                continue;
            }
            final String payload = outboxEventSerializer.serialize(row);
            final String key = row.getPayloadKey() != null ? row.getPayloadKey() : row.getAggregateId().toString();
            final java.util.UUID rowId = row.getId();
            final Instant sentAt = Instant.now();

            kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
                if (ex == null) {
                    resultHandler.handleSuccess(rowId, sentAt, outboxRepository);
                } else {
                    log.warn("Kafka publish failed for outbox {} topic {}: {}",
                            rowId, topic, ex.getMessage());
                    resultHandler.handleFailure(rowId, ex.getMessage(), maxAttempts, sentAt,
                            backoffCalculator, log, outboxRepository);
                }
            });
        }
    }

    private String resolveTopic(OutboxEvent row) {
        return switch (row.getEventType()) {
            case OutboxEventTypes.USER_REGISTERED -> KafkaTopics.IAM_USER_REGISTERED;
            case OutboxEventTypes.OTP_RESENT -> KafkaTopics.IAM_OTP_RESENT;
            case OutboxEventTypes.PASSWORD_RESET -> KafkaTopics.IAM_PASSWORD_RESET;
            case OutboxEventTypes.ACCOUNT_DELETION_REQUESTED,
                 OutboxEventTypes.ACCOUNT_DELETION_CANCELLED -> KafkaTopics.IAM_ACCOUNT_DELETION;
            case OutboxEventTypes.ACCOUNT_ANONYMIZED -> KafkaTopics.IAM_ACCOUNT_EVENTS;
            case OutboxEventTypes.SEND_SHARE_EMAIL,
                 OutboxEventTypes.SEND_REVOKE_NOTICE -> KafkaTopics.AUDIO_SHARE_EMAIL;
            default -> {
                log.warn("Unknown outbox event type {} for event {}", row.getEventType(), row.getId());
                yield null;
            }
        };
    }
}
