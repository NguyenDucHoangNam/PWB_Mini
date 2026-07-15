package com.pwb.backend.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String USER_EVENTS = "pwb.user.events";
    public static final String NOTIFICATION_EVENTS = "pwb.notification.events";
    public static final String AUDIT_EVENTS = "pwb.audit.events";

    @Bean
    public NewTopic userEventsTopic() {
        // replicas=1 chỉ dành cho local profile. Production cần replicas=3.
        return TopicBuilder.name(USER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        // replicas=1 chỉ dành cho local profile. Production cần replicas=3.
        return TopicBuilder.name(NOTIFICATION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        // replicas=1 chỉ dành cho local profile. Production cần replicas=3.
        return TopicBuilder.name(AUDIT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
