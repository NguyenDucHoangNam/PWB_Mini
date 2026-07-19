package com.pwb.outbox.infrastructure.logging;

import com.pwb.outbox.api.OutboxEventPayload;
import com.pwb.outbox.api.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Profile("test")
public class LoggingOutboxWriter implements OutboxWriter {
    @Override
    public void enqueue(String topic, String key, String aggregateType, OutboxEventPayload payload, Map<String, String> headers) {
        log.info("OUTBOX.enqueue: topic={} key={} aggregateType={} schemaVersion={} bodyLen={}",
            topic, key, aggregateType, payload.schemaVersion(), payload.body().length());
    }
}
