package com.pwb.infra.outbox.api;

public interface OutboxWriter {

    void enqueue(OutboxEnqueueRequested request);
}