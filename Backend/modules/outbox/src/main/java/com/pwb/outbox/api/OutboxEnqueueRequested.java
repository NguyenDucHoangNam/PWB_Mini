package com.pwb.outbox.api;

import java.util.Map;

public record OutboxEnqueueRequested(
    String topic,
    String key,
    String aggregateType,
    OutboxEventPayload payload,
    Map<String, String> headers
) {}
