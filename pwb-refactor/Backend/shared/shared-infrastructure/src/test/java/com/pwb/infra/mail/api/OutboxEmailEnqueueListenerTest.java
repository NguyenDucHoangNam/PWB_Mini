package com.pwb.infra.mail.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OutboxEmailEnqueueListener — convert EmailEvent to outbox request")
class OutboxEmailEnqueueListenerTest {

    private OutboxWriter outboxWriter;
    private ObjectMapper objectMapper;
    private OutboxEmailEnqueueListener listener;

    @BeforeEach
    void setUp() {
        outboxWriter = mock(OutboxWriter.class);
        objectMapper = new ObjectMapper();
        listener = new OutboxEmailEnqueueListener(outboxWriter, objectMapper);
    }

    @Test
    @DisplayName("should_enqueue_outbox_request_when_email_payload_valid")
    void should_enqueue_outbox_request_when_email_payload_valid() {
        EmailPayload payload = new EmailPayload(
                "welcome.html",
                "user@example.com",
                UUID.randomUUID(),
                Map.of("name", "John"),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                "Hello",
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(outboxWriter).enqueue(captor.capture());
        OutboxEnqueueRequested request = captor.getValue();

        assertThat(request.eventType()).isEqualTo("EmailPersisted");
        assertThat(request.topic()).isEqualTo("notification.email.v1");
        assertThat(request.aggregateType()).isEqualTo("Email");
        assertThat(request.aggregateId()).isEqualTo("user@example.com");
        assertThat(request.payloadKey()).isEqualTo("user@example.com");
        assertThat(request.payload().body()).contains("\"template\":\"welcome.html\"");
        assertThat(request.headers()).isEmpty();
    }

    @Test
    @DisplayName("should_skip_enqueue_when_to_email_blank")
    void should_skip_enqueue_when_to_email_blank() {
        EmailPayload payload = new EmailPayload(
                "welcome.html",
                "  ",
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                null,
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        verify(outboxWriter, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should_skip_enqueue_when_to_email_null")
    void should_skip_enqueue_when_to_email_null() {
        EmailPayload payload = new EmailPayload(
                "welcome.html",
                null,
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                null,
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        verify(outboxWriter, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should_use_email_notification_topic_constant")
    void should_use_email_notification_topic_constant() {
        EmailPayload payload = new EmailPayload(
                "welcome.html",
                "user@example.com",
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                null,
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(outboxWriter).enqueue(captor.capture());

        assertThat(captor.getValue().topic()).isEqualTo("notification.email.v1");
    }

    @Test
    @DisplayName("should_set_payload_schema_version_to_1")
    void should_set_payload_schema_version_to_1() {
        EmailPayload payload = new EmailPayload(
                "welcome.html",
                "user@example.com",
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                null,
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(outboxWriter).enqueue(captor.capture());

        assertThat(captor.getValue().payload().schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_not_throw_when_serialization_fails")
    void should_not_throw_when_serialization_fails() {
        objectMapper = mock(ObjectMapper.class);
        try {
            org.mockito.Mockito.when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        listener = new OutboxEmailEnqueueListener(outboxWriter, objectMapper);

        EmailPayload payload = new EmailPayload(
                "welcome.html",
                "user@example.com",
                UUID.randomUUID(),
                Map.of(),
                "vi",
                "Welcome",
                "<p>Hello</p>",
                null,
                "noreply@pwb.local");

        listener.onEmail(new EmailEventRequested(payload));

        verify(outboxWriter, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }
}