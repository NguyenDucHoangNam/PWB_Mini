package com.pwb.infra.outbox.scheduler;

import com.pwb.infra.it.AbstractPostgresKafkaIT;
import com.pwb.infra.it.SharedInfraTestApp;
import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import com.pwb.infra.outbox.sink.kafka.KafkaOutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = SharedInfraTestApp.class, properties = {
        "pwb.outbox.enabled=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("it")
@DisplayName("OutboxRelayScheduler — pick up PENDING events and hand off to publisher")
class OutboxRelaySchedulerIT extends AbstractPostgresKafkaIT {

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
    private OutboxRelayScheduler scheduler;

    @Autowired
    private OutboxEventJpaRepository repository;

    @MockitoBean
    private KafkaOutboxPublisher publisher;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should_pick_pending_event_when_ready")
    void should_pick_pending_event_when_ready() {
        List<OutboxEventJpaEntity> captured = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            captured.add(inv.getArgument(0));
            return null;
        }).when(publisher).publish(any());

        OutboxEventJpaEntity pending = buildPending("u-1");
        repository.saveAndFlush(pending);

        scheduler.relay();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getAggregateId()).isEqualTo("u-1");
    }

    @Test
    @DisplayName("should_skip_when_no_pending")
    void should_skip_when_no_pending() {
        scheduler.relay();

        Mockito.verify(publisher, Mockito.never()).publish(any());
    }

    @Test
    @DisplayName("should_not_re_pick_when_already_processing")
    void should_not_re_pick_when_already_processing() {
        OutboxEventJpaEntity processing = buildPending("u-2");
        processing.setStatus(OutboxStatus.PROCESSING);
        processing.setNextAttemptAt(Instant.now().plusSeconds(60));
        processing.setLeaseUntil(Instant.now().plusSeconds(60));
        repository.saveAndFlush(processing);

        scheduler.relay();

        Mockito.verify(publisher, Mockito.never()).publish(any());
    }

    @Test
    @DisplayName("should_reclaim_event_with_expired_lease")
    void should_reclaim_event_with_expired_lease() {
        List<OutboxEventJpaEntity> captured = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            captured.add(inv.getArgument(0));
            return null;
        }).when(publisher).publish(any());

        OutboxEventJpaEntity expired = buildPending("u-3");
        expired.setStatus(OutboxStatus.PROCESSING);
        expired.setLeaseUntil(Instant.now().minusSeconds(10));
        repository.saveAndFlush(expired);

        scheduler.relay();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getAggregateId()).isEqualTo("u-3");
    }

    @Test
    @DisplayName("should_respect_batch_size_limit")
    void should_respect_batch_size_limit() {
        List<OutboxEventJpaEntity> captured = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            captured.add(inv.getArgument(0));
            return null;
        }).when(publisher).publish(any());

        for (int i = 0; i < 5; i++) {
            repository.saveAndFlush(buildPending("u-" + i));
        }

        scheduler.relay();

        assertThat(captured.size()).isLessThanOrEqualTo(20);
        assertThat(captured.size()).isEqualTo(5);
    }

    private OutboxEventJpaEntity buildPending(String aggregateId) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.builder()
                .eventId(eventId)
                .eventType("UserPersisted")
                .aggregateType("User")
                .aggregateId(aggregateId)
                .topic("notification.email.v1")
                .payloadKey(aggregateId)
                .payload("{\"k\":\"v\"}")
                .headers(Map.of())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(now)
                .nextAttemptAt(now)
                .build();
        return entity;
    }
}
