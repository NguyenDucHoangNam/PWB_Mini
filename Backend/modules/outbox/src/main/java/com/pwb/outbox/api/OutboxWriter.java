package com.pwb.outbox.api;

import java.util.Map;

public interface OutboxWriter {
    void enqueue(String topic, String key, OutboxEventPayload payload, Map<String, String> headers);
}
