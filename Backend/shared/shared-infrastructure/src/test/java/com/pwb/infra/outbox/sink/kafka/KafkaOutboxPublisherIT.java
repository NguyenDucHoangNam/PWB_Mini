package com.pwb.infra.outbox.sink.kafka;

import com.pwb.infra.it.AbstractPostgresKafkaIT;
import com.pwb.infra.it.SharedInfraTestApp;
import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import com.pwb.infra.outbox.persistence.repository.OutboxEventJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SharedInfraTestApp.class, properties = {
        "pwb.outbox.enabled=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("it")
@DisplayName("KafkaOutboxPublisher — publish message to Kafka and update outbox status")
class KafkaOutboxPublisherIT extends AbstractPostgresKafkaIT {

    @DynamicPropertySource
    static void overrideDeps(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        KAFKA.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private final String topic = "publish-test-" + UUID.randomUUID();

    @Autowired
    private KafkaOutboxPublisher publisher;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should_publish_message_to_kafka_and_appear_on_topic")
    void should_publish_message_to_kafka_and_appear_on_topic() {
        OutboxEventJpaEntity entity = persistEntity("key-1");

        try (KafkaConsumer<String, String> consumer = createConsumer(topic)) {
            consumer.subscribe(List.of(topic));

            publisher.publish(entity);

            ConsumerRecord<String, String> record = pollRecord(consumer);

            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo("key-1");
            assertThat(record.value()).isEqualTo("{\"k\":\"v\"}");
        }
    }

    @Test
    @DisplayName("should_use_payload_key_as_kafka_key")
    void should_use_payload_key_as_kafka_key() {
        OutboxEventJpaEntity entity = persistEntity("aggregate-42");

        try (KafkaConsumer<String, String> consumer = createConsumer(topic)) {
            consumer.subscribe(List.of(topic));

            publisher.publish(entity);
            ConsumerRecord<String, String> record = pollRecord(consumer);

            assertThat(record.key()).isEqualTo("aggregate-42");
        }
    }

    @Test
    @DisplayName("should_set_outbox_status_to_sent_after_completion")
    void should_set_outbox_status_to_sent_after_completion() {
        OutboxEventJpaEntity entity = persistEntity("key-sent");

        publisher.publish(entity);

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OutboxEventJpaEntity reloaded = repository.findById(entity.getId()).orElseThrow();
                    assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.SENT);
                    assertThat(reloaded.getSentAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("should_publish_payload_body_unchanged")
    void should_publish_payload_body_unchanged() {
        OutboxEventJpaEntity entity = persistEntity("key-body");
        entity.setPayload("{\"hello\":\"world\",\"id\":123}");
        repository.saveAndFlush(entity);

        try (KafkaConsumer<String, String> consumer = createConsumer(topic)) {
            consumer.subscribe(List.of(topic));

            publisher.publish(entity);
            ConsumerRecord<String, String> record = pollRecord(consumer);

            assertThat(record.value()).isEqualTo("{\"hello\":\"world\",\"id\":123}");
        }
    }

    private OutboxEventJpaEntity persistEntity(String payloadKey) {
        Instant now = Instant.now();
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.builder()
                .eventId(UUID.randomUUID())
                .eventType("UserPersisted")
                .aggregateType("User")
                .aggregateId(payloadKey)
                .topic(topic)
                .payloadKey(payloadKey)
                .payload("{\"k\":\"v\"}")
                .headers(Map.of())
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .createdAt(now)
                .nextAttemptAt(now)
                .build();
        return repository.saveAndFlush(entity);
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> pollRecord(KafkaConsumer<String, String> consumer) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    var records = consumer.poll(Duration.ofMillis(500));
                    return records.iterator().hasNext() ? records.iterator().next() : null;
                }, java.util.Objects::nonNull);
    }
}