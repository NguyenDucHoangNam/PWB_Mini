package com.pwb.infra.outbox.api;

import java.util.Map;

public record OutboxEnqueueRequested(
        String eventType,
        String topic,
        String aggregateType,
        String aggregateId,
        String payloadKey,
        OutboxEventPayload payload,
        Map<String, String> headers
) {
}