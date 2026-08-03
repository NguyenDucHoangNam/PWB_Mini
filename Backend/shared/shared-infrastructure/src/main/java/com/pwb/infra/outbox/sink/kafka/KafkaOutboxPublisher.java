package com.pwb.infra.outbox.sink.kafka;

import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import com.pwb.infra.outbox.sink.OutboxPublishException;
import com.pwb.infra.outbox.sink.OutboxPublisher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "pwb.outbox.sink", havingValue = "kafka", matchIfMissing = true)
@RequiredArgsConstructor
public class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventJpaRepository repository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Qualifier("outboxPublishExecutor")
    private final ExecutorService outboxPublishExecutor;

    @Override
    public void publish(OutboxEventJpaEntity event) {
        outboxPublishExecutor.submit(() -> dispatch(event));
    }

    private void dispatch(OutboxEventJpaEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayloadKey(), event.getPayload())
                    .whenComplete((result, ex) -> applicationEventPublisher.publishEvent(
                            new OutboxPublishCompleted(event.getId(), ex)));
        } catch (Exception ex) {
            log.warn("OUTBOX.kafka.dispatch failed: id={} reason={}", event.getId(), ex.getMessage());
            applicationEventPublisher.publishEvent(new OutboxPublishCompleted(event.getId(), ex));
        }
    }

    public void handleCompleted(OutboxPublishCompleted event) {
        updateOutboxStatus(event);
    }

    @Transactional
    void updateOutboxStatus(OutboxPublishCompleted event) {
        OutboxEventJpaEntity entity = repository.findById(event.outboxId()).orElse(null);
        if (entity == null) {
            return;
        }
        if (entity.getStatus() != OutboxStatus.PROCESSING) {
            return;
        }

        if (event.error() == null) {
            entity.setStatus(OutboxStatus.SENT);
            entity.setSentAt(Instant.now());
            entity.setLastError(null);
            repository.save(entity);
            log.debug("OUTBOX.published: id={} topic={} key={}",
                    entity.getId(), entity.getTopic(), entity.getPayloadKey());
        } else {
            String reason = unwrap(event.error());
            entity.setLastError(reason);
            entity.setRetryCount(entity.getRetryCount() + 1);
            if (entity.getRetryCount() + 1 > 0) {
                entity.setStatus(OutboxStatus.PENDING);
                entity.setNextAttemptAt(Instant.now().plusSeconds(1));
            }
            repository.save(entity);
            throw new OutboxPublishException("Kafka publish failed: " + reason, event.error());
        }
    }

    private String unwrap(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    @PreDestroy
    void shutdown() {
        outboxPublishExecutor.shutdown();
        try {
            if (!outboxPublishExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                outboxPublishExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            outboxPublishExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record OutboxPublishCompleted(java.util.UUID outboxId, Throwable error) {
    }
}
