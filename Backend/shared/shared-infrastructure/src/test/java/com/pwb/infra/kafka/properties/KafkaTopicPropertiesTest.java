package com.pwb.infra.kafka.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaTopicProperties — default topic values")
class KafkaTopicPropertiesTest {

    private KafkaTopicProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KafkaTopicProperties();
    }

    @Test
    @DisplayName("should_default_email_topic_to_notification_v1")
    void should_default_email_topic_to_notification_v1() {
        assertThat(properties.getEmail()).isEqualTo("notification.email.v1");
        assertThat(properties.getEmail()).isEqualTo(KafkaTopicProperties.TOPIC_EMAIL);
    }

    @Test
    @DisplayName("should_default_voice_processing_topic")
    void should_default_voice_processing_topic() {
        assertThat(properties.getVoiceProcessing()).isEqualTo("voice.processing.v1");
        assertThat(properties.getVoiceProcessing()).isEqualTo(KafkaTopicProperties.TOPIC_VOICE_PROCESSING);
    }

    @Test
    @DisplayName("should_default_iam_audit_topic")
    void should_default_iam_audit_topic() {
        assertThat(properties.getIamAudit()).isEqualTo("iam.audit.v1");
        assertThat(properties.getIamAudit()).isEqualTo(KafkaTopicProperties.TOPIC_IAM_AUDIT);
    }

    @Test
    @DisplayName("should_allow_overriding_email_topic_via_setter")
    void should_allow_overriding_email_topic_via_setter() {
        properties.setEmail("custom.email.v2");

        assertThat(properties.getEmail()).isEqualTo("custom.email.v2");
    }
}