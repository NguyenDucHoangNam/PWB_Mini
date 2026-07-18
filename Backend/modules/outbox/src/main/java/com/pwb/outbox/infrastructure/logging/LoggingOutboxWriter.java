package com.pwb.outbox.infrastructure.logging;

import com.pwb.outbox.api.OutboxEventPayload;
import com.pwb.outbox.api.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class LoggingOutboxWriter implements OutboxWriter {
    @Override
    public void enqueue(String topic, String key, OutboxEventPayload payload, Map<String, String> headers) {
        log.info("OUTBOX.enqueue: topic={} key={} schemaVersion={} bodyLen={}",
            topic, key, payload.schemaVersion(), payload.body().length());
    }
}
