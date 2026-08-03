package com.pwb.iam.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.iam.domain.audit.AuditEventType;
import com.pwb.iam.domain.audit.AuditLogEntry;
import com.pwb.infra.outbox.api.OutboxEnqueueHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPersistListenerTest {

    @Mock private OutboxEnqueueHelper outboxEnqueueHelper;

    private ObjectMapper objectMapper;
    private AuditPersistListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new AuditPersistListener(outboxEnqueueHelper, objectMapper);
    }

    private AuditLogEntry sampleEntry() {
        return AuditLogEntry.of(AuditEventType.LOGIN_SUCCESS, UUID.randomUUID(), "user@example.com",
                "10.0.0.1", "ua", true, null, null);
    }

    @Test
    @DisplayName("should serialize entry as JSON and enqueue to outbox")
    void should_serialize_and_enqueue() {
        AuditLogEntry entry = sampleEntry();

        listener.onAudit(new AuditPersistRequested(entry));

        verify(outboxEnqueueHelper).enqueue(
                eq("iam.audit.v1"),
                eq("AuditLog"),
                eq(entry.eventId().toString()),
                anyString());
    }

    @Test
    @DisplayName("should silently skip when serialization fails")
    void should_skip_on_serialization_failure() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new JsonProcessingException("boom") {});
        AuditPersistListener local = new AuditPersistListener(outboxEnqueueHelper, failingMapper);

        local.onAudit(new AuditPersistRequested(sampleEntry()));

        verify(outboxEnqueueHelper, never()).enqueue(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should preserve metadata in JSON")
    void should_preserve_metadata() {
        AuditLogEntry entry = new AuditLogEntry(
                UUID.randomUUID(), AuditEventType.LOGIN_FAILED, null, "user@example.com", null,
                "10.0.0.1", "ua", false, "BAD_CREDENTIALS",
                Map.of("lockoutReason", "BAD_CREDENTIALS"), java.time.Instant.now());

        listener.onAudit(new AuditPersistRequested(entry));

        verify(outboxEnqueueHelper).enqueue(
                eq("iam.audit.v1"),
                eq("AuditLog"),
                eq(entry.eventId().toString()),
                anyString());
    }
}