package com.pwb.infra.kafka.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "kafka.topics")
public class KafkaTopicProperties {

    public static final String TOPIC_EMAIL = "notification.email.v1";
    public static final String TOPIC_VOICE_PROCESSING = "voice.processing.v1";

    private String email = TOPIC_EMAIL;
    private String voiceProcessing = TOPIC_VOICE_PROCESSING;
}