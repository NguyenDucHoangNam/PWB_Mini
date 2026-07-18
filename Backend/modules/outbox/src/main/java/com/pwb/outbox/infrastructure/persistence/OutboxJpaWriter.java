package com.pwb.outbox.infrastructure.persistence;

import com.pwb.outbox.api.OutboxEventPayload;
import com.pwb.outbox.api.OutboxWriter;
import com.pwb.outbox.core.model.OutboxStatus;
import com.pwb.outbox.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.pwb.outbox.infrastructure.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OutboxJpaWriter implements OutboxWriter {

    private final OutboxEventJpaRepository repository;

    @Override
    public void enqueue(String topic, String key, OutboxEventPayload payload, Map<String, String> headers) {
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.builder()
            .aggregateType("User")
            .aggregateId(key)
            .topic(topic)
            .payloadKey(key)
            .payload(payload.body())
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .createdAt(Instant.now())
            .nextAttemptAt(Instant.now())
            .build();
        repository.save(entity);
        log.debug("OUTBOX.persisted: topic={} key={} id={}", topic, key, entity.getId());
    }
}
