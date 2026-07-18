package com.pwb.outbox.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class OutboxKafkaConfig {

    public static final String TOPIC_EMAIL = "notification.email.v1";

    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name(TOPIC_EMAIL)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
