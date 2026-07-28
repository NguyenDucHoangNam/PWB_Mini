package com.pwb.infra.mail.api;

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
public class OutboxEmailEnqueueListener {

    private static final String EMAIL_TOPIC = "notification.email.v1";
    private static final String AGGREGATE_TYPE = "Email";

    private final OutboxEnqueueHelper outboxEnqueueHelper;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmail(EmailEventRequested event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException ex) {
            log.warn("Email serialization failed: toEmail={} template={}", event.payload().toEmail(), event.payload().template(), ex);
            return;
        }
        outboxEnqueueHelper.enqueue(
                EMAIL_TOPIC,
                AGGREGATE_TYPE,
                event.payload().toEmail(),
                json
        );
    }
}