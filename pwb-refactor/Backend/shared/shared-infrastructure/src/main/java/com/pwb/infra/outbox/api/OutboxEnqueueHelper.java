package com.pwb.infra.outbox.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxEnqueueHelper {

    private final OutboxWriter writer;

    public void enqueue(String topic, String aggregateType, String aggregateId, String payloadJson) {
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                aggregateType + "Persisted",
                topic,
                aggregateType,
                aggregateId,
                aggregateId,
                OutboxEventPayload.of(payloadJson),
                Map.of()
        );
        writer.enqueue(request);
    }
}