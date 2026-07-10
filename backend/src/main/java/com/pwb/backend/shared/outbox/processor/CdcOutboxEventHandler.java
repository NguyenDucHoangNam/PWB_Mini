package com.pwb.backend.shared.outbox.processor;

import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import io.debezium.engine.RecordChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Bridges Debezium change events into the outbox processing pipeline.
 *
 * <p>H3: previously ran without a {@code @Transactional} boundary, so a
 * crash between {@code findById} and {@code processOutboxEvent} could
 * replay the event. We now wrap {@link #handleEvent} in a single
 * {@code REQUIRES_NEW} transaction so the row lock and the processor's
 * status mutation commit atomically. The CDC consumer is single-threaded
 * per connector, but multiple connectors can still overlap.
 */
@Slf4j
public class CdcOutboxEventHandler<T extends OutboxEvent> {

    private final JpaRepository<T, String> repository;
    private final OutboxEventProcessor<T> processor;
    private final String aggregateTypeFilter;

    public CdcOutboxEventHandler(JpaRepository<T, String> repository, OutboxEventProcessor<T> processor) {
        this(repository, processor, null);
    }

    public CdcOutboxEventHandler(
        JpaRepository<T, String> repository,
        OutboxEventProcessor<T> processor,
        String aggregateTypeFilter) {
        this.repository = repository;
        this.processor = processor;
        this.aggregateTypeFilter = aggregateTypeFilter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleEvent(RecordChangeEvent<SourceRecord> event) {
        SourceRecord sourceRecord = event.record();
        Struct value = (Struct) sourceRecord.value();

        if (value == null) {
            return;
        }

        Struct after = value.getStruct("after");
        String op = value.getString("op");

        if (after == null || !("c".equals(op) || "r".equals(op))) {
            return;
        }

        try {
            String eventId = after.getString("id");

            if (aggregateTypeFilter != null) {
                String aggregateType = after.getString("aggregate_type");
                if (!aggregateTypeFilter.equals(aggregateType)) {
                    return;
                }
            }

            Optional<T> outboxEventOpt = repository.findById(eventId);

            if (outboxEventOpt.isPresent()) {
                T outboxEvent = outboxEventOpt.get();

                if (outboxEvent.getStatus() == OutboxEventStatus.PENDING) {
                    log.info("CDC received pending outbox event: {} (aggregateType={})",
                        eventId, outboxEvent.getAggregateType());
                    processor.processOutboxEvent(outboxEvent);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process CDC event", e);
        }
    }
}