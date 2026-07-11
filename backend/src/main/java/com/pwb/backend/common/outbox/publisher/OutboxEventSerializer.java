package com.pwb.backend.common.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.common.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventSerializer {

    private static final Set<String> USER_EVENT_TYPES = Set.of(
            OutboxEventTypes.USER_REGISTERED,
            OutboxEventTypes.OTP_RESENT);

    private final ObjectMapper objectMapper;


    public String serialize(OutboxEvent event) {
        try {
            if (USER_EVENT_TYPES.contains(event.getEventType())) {
                UserRegisteredEvent payload = objectMapper.readValue(
                        event.getPayload(), UserRegisteredEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            return event.getPayload();
        } catch (Exception ex) {
            log.warn("Outbox serialization fallback for event {}: {}", event.getId(), ex.getMessage());
            return event.getPayload();
        }
    }
}