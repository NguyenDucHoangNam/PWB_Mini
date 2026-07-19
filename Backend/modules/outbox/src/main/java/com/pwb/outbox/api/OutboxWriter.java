package com.pwb.outbox.api;

import java.util.Map;

public interface OutboxWriter {
    void enqueue(String topic, String key, String aggregateType, OutboxEventPayload payload, Map<String, String> headers);
}
