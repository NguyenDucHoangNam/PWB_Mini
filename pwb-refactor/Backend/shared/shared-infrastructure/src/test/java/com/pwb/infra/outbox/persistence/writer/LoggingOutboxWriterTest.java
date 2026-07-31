package com.pwb.infra.outbox.persistence.writer;

import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("LoggingOutboxWriter — log-only fallback for test profile")
class LoggingOutboxWriterTest {

    private final LoggingOutboxWriter writer = new LoggingOutboxWriter();

    @Test
    @DisplayName("should_log_event_metadata_when_enqueue_called")
    void should_log_event_metadata_when_enqueue_called(CapturedOutput output) {
        OutboxEnqueueRequested request = createRequest();

        writer.enqueue(request);

        assertThat(output.getOut())
                .contains("OUTBOX.enqueue")
                .contains("eventType=EmailPersisted")
                .contains("topic=notification.email.v1")
                .contains("aggregateType=Email")
                .contains("schemaVersion=1")
                .contains("bodyLen=");
    }

    @Test
    @DisplayName("should_not_throw_when_enqueue_called")
    void should_not_throw_when_enqueue_called() {
        OutboxEnqueueRequested request = createRequest();

        assertThatCode(() -> writer.enqueue(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should_not_log_payload_body_content")
    void should_not_log_payload_body_content(CapturedOutput output) {
        String sensitiveBody = "{\"password\":\"super-secret-123\"}";
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                "EmailPersisted",
                "notification.email.v1",
                "Email",
                "u-1",
                "u-1",
                OutboxEventPayload.of(sensitiveBody),
                Map.of());

        writer.enqueue(request);

        assertThat(output.getOut()).doesNotContain("super-secret-123");
        assertThat(output.getOut()).doesNotContain("password");
        assertThat(output.getOut()).contains("bodyLen=");
    }

    @Test
    @DisplayName("should_handle_empty_headers_gracefully")
    void should_handle_empty_headers_gracefully(CapturedOutput output) {
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                "UserPersisted",
                "iam.audit.v1",
                "User",
                "u-1",
                "u-1",
                OutboxEventPayload.of("{}"),
                Map.of());

        writer.enqueue(request);

        assertThat(output.getOut())
                .contains("OUTBOX.enqueue")
                .contains("eventType=UserPersisted");
    }

    private OutboxEnqueueRequested createRequest() {
        return new OutboxEnqueueRequested(
                "EmailPersisted",
                "notification.email.v1",
                "Email",
                "user@example.com",
                "user@example.com",
                OutboxEventPayload.of("{\"to\":\"x@y\",\"body\":\"hello\"}"),
                Map.of());
    }
}