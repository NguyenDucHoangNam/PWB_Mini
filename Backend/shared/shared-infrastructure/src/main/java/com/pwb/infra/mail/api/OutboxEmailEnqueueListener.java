package com.pwb.infra.mail.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.kafka.properties.KafkaTopicProperties;
import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxEventPayload;
import com.pwb.infra.outbox.api.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEmailEnqueueListener {

    private static final String EVENT_TYPE = "EmailPersisted";
    private static final String AGGREGATE_TYPE = "Email";

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEmail(EmailEventRequested event) {
        EmailPayload payload = event.payload();
        String toEmail = payload.toEmail();
        String topic = KafkaTopicProperties.TOPIC_EMAIL;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Email serialization failed: template={} reason={}",
                    payload.template(), ex.getMessage());
            return;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Email enqueue skipped: blank toEmail, template={}", payload.template());
            return;
        }

        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                EVENT_TYPE,
                topic,
                AGGREGATE_TYPE,
                toEmail,
                toEmail,
                OutboxEventPayload.of(json),
                Map.of()
        );
        outboxWriter.enqueue(request);
        log.debug("OUTBOX.email.persisted: template={} to={} topic={}", payload.template(), toEmail, topic);
    }
}
