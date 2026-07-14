package com.pwb.backend.common.outbox.publisher;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.outbox.event.AccountDeletionCancelledEvent;
import com.pwb.backend.common.outbox.event.AccountDeletionRequestedEvent;
import com.pwb.backend.common.outbox.event.PasswordResetRequestedEvent;
import com.pwb.backend.common.outbox.event.ShareEmailEvent;
import com.pwb.backend.common.outbox.event.UserRegisteredEvent;
import com.pwb.backend.common.outbox.model.OutboxEvent;
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

    private static final Set<String> SHARE_EVENT_TYPES = Set.of(
            OutboxEventTypes.SEND_SHARE_EMAIL,
            OutboxEventTypes.SEND_REVOKE_NOTICE);

    private final ObjectMapper objectMapper;
    private final OutboxPayloadCipher cipher;

    public String serialize(OutboxEvent event) {
        String plaintext = cipher.isEnabled()
                ? safeDecrypt(event.getPayload(), event.getId())
                : event.getPayload();
        try {
            if (USER_EVENT_TYPES.contains(event.getEventType())) {
                UserRegisteredEvent payload = objectMapper.readValue(plaintext, UserRegisteredEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            if (PASSWORD_RESET_EVENT_TYPES.contains(event.getEventType())) {
                PasswordResetRequestedEvent payload = objectMapper.readValue(plaintext, PasswordResetRequestedEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            if (DELETION_EVENT_TYPES.contains(event.getEventType())) {
                if (OutboxEventTypes.ACCOUNT_DELETION_REQUESTED.equals(event.getEventType())) {
                    AccountDeletionRequestedEvent payload = objectMapper.readValue(plaintext, AccountDeletionRequestedEvent.class);
                    return objectMapper.writeValueAsString(payload);
                }
                AccountDeletionCancelledEvent payload = objectMapper.readValue(plaintext, AccountDeletionCancelledEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            if (SHARE_EVENT_TYPES.contains(event.getEventType())) {
                ShareEmailEvent payload = objectMapper.readValue(plaintext, ShareEmailEvent.class);
                return objectMapper.writeValueAsString(payload);
            }
            return plaintext;
        } catch (Exception ex) {
            log.warn("Outbox serialization fallback for event {}: {}", event.getId(), ex.getMessage());
            return plaintext;
        }
    }

    private String safeDecrypt(String stored, UUID eventId) {
        if (stored == null || !cipher.isEncrypted(stored)) {
            return stored;
        }
        try {
            return cipher.decrypt(stored);
        } catch (Exception ex) {
            log.error("OUTBOX_PAYLOAD_DECRYPT_FAILED eventId={} reason={}", eventId, ex.getMessage());
            throw ex;
        }
    }
}
