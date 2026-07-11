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
        throw new IllegalArgumentException("Unknown outbox eventType: " + eventType);
    }
}