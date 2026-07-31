package com.pwb.infra.outbox.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OutboxEnqueueHelper — delegate to OutboxWriter")
class OutboxEnqueueHelperTest {

    private OutboxWriter writer;
    private OutboxEnqueueHelper helper;

    @BeforeEach
    void setUp() {
        writer = mock(OutboxWriter.class);
        helper = new OutboxEnqueueHelper(writer);
    }

    @Test
    @DisplayName("should_delegate_to_writer_with_topic_aggregate_payload_overload_1")
    void should_delegate_to_writer_with_topic_aggregate_payload_overload_1() {
        helper.enqueue("topic.x", "User", "u-1", "{\"k\":\"v\"}");

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());
        OutboxEnqueueRequested request = captor.getValue();

        assertThat(request.topic()).isEqualTo("topic.x");
        assertThat(request.aggregateType()).isEqualTo("User");
        assertThat(request.aggregateId()).isEqualTo("u-1");
        assertThat(request.eventType()).isEqualTo("UserPersisted");
        assertThat(request.payloadKey()).isEqualTo("u-1");
        assertThat(request.payload().body()).isEqualTo("{\"k\":\"v\"}");
        assertThat(request.payload().schemaVersion()).isEqualTo(1);
        assertThat(request.headers()).isEmpty();
    }

    @Test
    @DisplayName("should_delegate_to_writer_with_full_overload_2")
    void should_delegate_to_writer_with_full_overload_2() {
        Map<String, String> headers = Map.of("correlationId", "abc-123");
        OutboxEventPayload payload = OutboxEventPayload.of("payload-body");

        helper.enqueue("topic.x", "User", "u-1", "UserCreated", "key-1", headers, payload);

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());
        OutboxEnqueueRequested request = captor.getValue();

        assertThat(request.topic()).isEqualTo("topic.x");
        assertThat(request.aggregateType()).isEqualTo("User");
        assertThat(request.aggregateId()).isEqualTo("u-1");
        assertThat(request.eventType()).isEqualTo("UserCreated");
        assertThat(request.payloadKey()).isEqualTo("key-1");
        assertThat(request.payload()).isSameAs(payload);
        assertThat(request.headers()).isSameAs(headers);
    }

    @Test
    @DisplayName("should_pass_event_type_directly_in_overload_2")
    void should_pass_event_type_directly_in_overload_2() {
        helper.enqueue("topic", "Agg", "agg-1", "CustomEvent", "k", Map.of(), OutboxEventPayload.of("{}"));

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());

        assertThat(captor.getValue().eventType()).isEqualTo("CustomEvent");
    }

    @Test
    @DisplayName("should_derive_event_type_from_aggregate_type_in_overload_1")
    void should_derive_event_type_from_aggregate_type_in_overload_1() {
        helper.enqueue("topic", "Order", "o-1", "{}");

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());

        assertThat(captor.getValue().eventType()).isEqualTo("OrderPersisted");
    }

    @Test
    @DisplayName("should_use_aggregate_id_as_payload_key_in_overload_1")
    void should_use_aggregate_id_as_payload_key_in_overload_1() {
        helper.enqueue("topic", "Order", "order-42", "{}");

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());

        assertThat(captor.getValue().payloadKey()).isEqualTo("order-42");
    }

    @Test
    @DisplayName("should_pass_empty_headers_in_overload_1")
    void should_pass_empty_headers_in_overload_1() {
        helper.enqueue("topic", "Order", "o-1", "{}");

        ArgumentCaptor<OutboxEnqueueRequested> captor = ArgumentCaptor.forClass(OutboxEnqueueRequested.class);
        verify(writer).enqueue(captor.capture());

        assertThat(captor.getValue().headers()).isEmpty();
    }
}