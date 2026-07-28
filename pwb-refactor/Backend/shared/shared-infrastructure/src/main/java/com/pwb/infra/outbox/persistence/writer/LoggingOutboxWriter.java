package com.pwb.infra.outbox.persistence.writer;

import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test")
public class LoggingOutboxWriter implements OutboxWriter {

    @Override
    public void enqueue(OutboxEnqueueRequested request) {
        log.info("OUTBOX.enqueue: eventType={} topic={} aggregateType={} schemaVersion={} bodyLen={}",
                request.eventType(), request.topic(), request.aggregateType(),
                request.payload().schemaVersion(), request.payload().body().length());
    }
}