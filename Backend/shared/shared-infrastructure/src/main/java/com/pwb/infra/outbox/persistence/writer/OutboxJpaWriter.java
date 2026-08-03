package com.pwb.infra.outbox.persistence.writer;

import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxWriter;
import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OutboxJpaWriter implements OutboxWriter {

    private final OutboxEventJpaRepository repository;

    @Override
    public void enqueue(OutboxEnqueueRequested request) {
        validate(request);
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.builder()
                .eventId(UUID.randomUUID())
                .eventType(request.eventType())
                .aggregateType(request.aggregateType())
                .aggregateId(request.aggregateId())
                .topic(request.topic())
                .payloadKey(request.payloadKey())
                .payload(request.payload().body())
                .headers(request.headers())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .nextAttemptAt(Instant.now())
                .build();
        repository.save(entity);
        log.debug("OUTBOX.persisted: eventType={} topic={} key={} id={}",
                request.eventType(), request.topic(), request.payloadKey(), entity.getId());
    }

    private void validate(OutboxEnqueueRequested request) {
        if (request.topic() == null || request.topic().isBlank()) {
            throw new IllegalArgumentException("Outbox topic is required");
        }
        if (request.aggregateType() == null || request.aggregateType().isBlank()) {
            throw new IllegalArgumentException(
                    "Outbox aggregateType is required (topic=" + request.topic() + ")");
        }
        if (request.aggregateId() == null || request.aggregateId().isBlank()) {
            throw new IllegalArgumentException(
                    "Outbox aggregateId is required (topic=" + request.topic() + ")");
        }
        if (request.payload() == null || request.payload().body() == null) {
            throw new IllegalArgumentException(
                    "Outbox payload is required (topic=" + request.topic() + ")");
        }
    }
}