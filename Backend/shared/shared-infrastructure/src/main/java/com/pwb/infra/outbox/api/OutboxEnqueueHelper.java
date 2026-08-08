package com.pwb.infra.outbox.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxEnqueueHelper {

    private final OutboxWriter writer;

    public void enqueue(String topic, String aggregateType, String aggregateId, String payloadJson) {
        enqueue(topic, aggregateType, aggregateId, aggregateType + "Persisted", aggregateId,
                Map.of(), OutboxEventPayload.of(payloadJson));
    }

    public void enqueue(String topic, String aggregateType, String aggregateId,
                        String eventType, String payloadKey,
                        Map<String, String> headers, OutboxEventPayload payload) {
        writer.enqueue(new OutboxEnqueueRequested(
                eventType, topic, aggregateType, aggregateId, payloadKey, payload, headers));
    }
}