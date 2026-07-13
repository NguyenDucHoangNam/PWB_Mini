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
        if (OutboxEventTypes.SEND_SHARE_EMAIL.equals(eventType)
                || OutboxEventTypes.SEND_REVOKE_NOTICE.equals(eventType)) {
            return KafkaTopics.AUDIO_SHARE_EMAIL;
        }
        if (OutboxEventTypes.ROOM_LIFECYCLE_ENDED.equals(eventType)) {
            return KafkaTopics.LIVEROOM_LIFECYCLE;
        }
        throw new IllegalArgumentException("Unknown outbox eventType: " + eventType);
    }
}