package com.pwb.backend.common.outbox.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.outbox.event.AccountDeletionCancelledEvent;
import com.pwb.backend.common.outbox.event.AccountDeletionRequestedEvent;
import com.pwb.backend.common.outbox.event.PasswordResetRequestedEvent;
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

    private static final Set<String> PASSWORD_RESET_EVENT_TYPES = Set.of(
            OutboxEventTypes.PASSWORD_RESET);

    private static final Set<String> DELETION_EVENT_TYPES = Set.of(
            OutboxEventTypes.ACCOUNT_DELETION_REQUESTED,
            OutboxEventTypes.ACCOUNT_DELETION_CANCELLED);

    private final ObjectMapper objectMapper;


    public String serialize(OutboxEvent event) {
        try {
            if (USER_EVENT_TYPES.contains(event.getEventType())) {
                UserRegisteredEvent payload = objectMapper.readValue(
                        event.getPayload(), UserRegisteredEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            if (PASSWORD_RESET_EVENT_TYPES.contains(event.getEventType())) {
                PasswordResetRequestedEvent payload = objectMapper.readValue(
                        event.getPayload(), PasswordResetRequestedEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            if (DELETION_EVENT_TYPES.contains(event.getEventType())) {
                if (OutboxEventTypes.ACCOUNT_DELETION_REQUESTED.equals(event.getEventType())) {
                    AccountDeletionRequestedEvent payload = objectMapper.readValue(
                            event.getPayload(), AccountDeletionRequestedEvent.class);
                    return objectMapper.writeValueAsString(payload);
                }
                AccountDeletionCancelledEvent payload = objectMapper.readValue(
                        event.getPayload(), AccountDeletionCancelledEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            return event.getPayload();
        } catch (Exception ex) {
            log.warn("Outbox serialization fallback for event {}: {}", event.getId(), ex.getMessage());
            return event.getPayload();
        }
    }
}