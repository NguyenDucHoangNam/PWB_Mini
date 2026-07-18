package com.pwb.outbox.api;

public record OutboxEventPayload(String body, int schemaVersion) {
    public static OutboxEventPayload of(String body) {
        return new OutboxEventPayload(body, 1);
    }
}
