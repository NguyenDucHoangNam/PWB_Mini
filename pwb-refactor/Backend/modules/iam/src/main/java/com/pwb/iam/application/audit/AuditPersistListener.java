package com.pwb.iam.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPersistListener {

    private static final String AUDIT_TOPIC_DEFAULT = "iam.audit.v1";
    private static final String AGGREGATE_TYPE = "AuditLog";

    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAudit(AuditPersistRequested event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event.entry());
        } catch (JsonProcessingException ex) {
            log.warn("Audit serialization failed: eventType={}",
                    event.entry().eventType(), ex);
            return;
        }
        outboxEnqueueHelper.enqueue(
                AUDIT_TOPIC_DEFAULT,
                AGGREGATE_TYPE,
                event.entry().eventId().toString(),
                json
        );
    }
}