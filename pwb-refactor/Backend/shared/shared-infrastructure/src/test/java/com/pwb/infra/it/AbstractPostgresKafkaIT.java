package com.pwb.infra.it;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public abstract class AbstractPostgresKafkaIT extends AbstractKafkaIT {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pwb_test")
            .withUsername("pwb_test")
            .withPassword("pwb_test");

    protected static KafkaContainer kafkaContainer() {
        return KAFKA;
    }
}