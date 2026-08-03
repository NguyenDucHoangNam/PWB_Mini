package com.pwb.infra.outbox.persistence.writer;

import com.pwb.infra.it.AbstractPostgresKafkaIT;
import com.pwb.infra.it.SharedInfraTestApp;
import com.pwb.infra.outbox.api.OutboxEnqueueRequested;
import com.pwb.infra.outbox.api.OutboxEventPayload;
import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SharedInfraTestApp.class, properties = {
        "pwb.outbox.enabled=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("it")
@DisplayName("OutboxJpaWriter — persist outbox events to Postgres")
class OutboxJpaWriterIT extends AbstractPostgresKafkaIT {

    @DynamicPropertySource
    static void overrideDeps(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        KAFKA.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private OutboxJpaWriter writer;

    @Autowired
    private OutboxEventJpaRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should_persist_event_with_pending_status")
    void should_persist_event_with_pending_status() {
        OutboxEnqueueRequested request = createRequest();

        writer.enqueue(request);

        List<OutboxEventJpaEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("should_set_created_at_and_next_attempt_at_to_now")
    void should_set_created_at_and_next_attempt_at_to_now() {
        Instant before = Instant.now();
        writer.enqueue(createRequest());
        Instant after = Instant.now();

        OutboxEventJpaEntity entity = repository.findAll().get(0);

        assertThat(entity.getCreatedAt()).isBetween(before, after);
        assertThat(entity.getNextAttemptAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("should_carry_aggregate_type_and_id")
    void should_carry_aggregate_type_and_id() {
        OutboxEnqueueRequested request = createRequest();

        writer.enqueue(request);

        OutboxEventJpaEntity entity = repository.findAll().get(0);
        assertThat(entity.getAggregateType()).isEqualTo("User");
        assertThat(entity.getAggregateId()).isEqualTo("u-1");
        assertThat(entity.getTopic()).isEqualTo("notification.email.v1");
        assertThat(entity.getPayloadKey()).isEqualTo("u-1");
    }

    @Test
    @DisplayName("should_initialize_retry_count_to_zero")
    void should_initialize_retry_count_to_zero() {
        writer.enqueue(createRequest());

        OutboxEventJpaEntity entity = repository.findAll().get(0);
        assertThat(entity.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("should_throw_when_topic_blank")
    void should_throw_when_topic_blank() {
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                "UserPersisted",
                "  ",
                "User",
                "u-1",
                "u-1",
                OutboxEventPayload.of("{}"),
                Map.of());

        assertThatThrownBy(() -> writer.enqueue(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    @DisplayName("should_throw_when_persistence_fails_with_invalid_payload")
    void should_throw_when_persistence_fails_with_invalid_payload() {
        OutboxEnqueueRequested request = new OutboxEnqueueRequested(
                "UserPersisted",
                "topic",
                "User",
                "u-1",
                "u-1",
                OutboxEventPayload.of(null),
                Map.of());

        assertThatThrownBy(() -> writer.enqueue(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OutboxEnqueueRequested createRequest() {
        return new OutboxEnqueueRequested(
                "UserPersisted",
                "notification.email.v1",
                "User",
                "u-1",
                "u-1",
                OutboxEventPayload.of("{\"k\":\"v\"}"),
                Map.of());
    }
}