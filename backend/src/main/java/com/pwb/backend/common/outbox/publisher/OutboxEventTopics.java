package com.pwb.backend.common.outbox.publisher;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventTopics {

    public String resolveTopic(String eventType) {
        if (OutboxEventTypes.USER_REGISTERED.equals(eventType)
                || OutboxEventTypes.OTP_RESENT.equals(eventType)) {
            return KafkaTopics.IAM_USER_REGISTERED;
        }
        if (OutboxEventTypes.PASSWORD_RESET.equals(eventType)) {
            return KafkaTopics.IAM_PASSWORD_RESET;
        }
        if (OutboxEventTypes.ACCOUNT_DELETION_REQUESTED.equals(eventType)
                || OutboxEventTypes.ACCOUNT_DELETION_CANCELLED.equals(eventType)) {
            return KafkaTopics.IAM_ACCOUNT_DELETION;
        }
        if (OutboxEventTypes.ACCOUNT_ANONYMIZED.equals(eventType)) {
            return KafkaTopics.IAM_ACCOUNT_EVENTS;
        }
        throw new IllegalArgumentException("Unknown outbox eventType: " + eventType);
    }
}