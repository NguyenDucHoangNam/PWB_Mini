package com.pwb.backend.common.outbox.publisher;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OutboxEventTopics {

    private static final Map<String, String> EVENT_TYPE_TO_TOPIC;

    static {
        EVENT_TYPE_TO_TOPIC = new HashMap<>();
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.USER_REGISTERED, KafkaTopics.IAM_USER_REGISTERED);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.USER_VERIFIED, KafkaTopics.IAM_USER_VERIFIED);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.OTP_RESENT, KafkaTopics.IAM_OTP_RESENT);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.PASSWORD_RESET, KafkaTopics.IAM_PASSWORD_RESET);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.ACCOUNT_DELETION_REQUESTED, KafkaTopics.IAM_ACCOUNT_DELETION);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.ACCOUNT_DELETION_CANCELLED, KafkaTopics.IAM_ACCOUNT_DELETION);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.ACCOUNT_ANONYMIZED, KafkaTopics.IAM_ACCOUNT_EVENTS);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.SEND_SHARE_EMAIL, KafkaTopics.AUDIO_SHARE_EMAIL);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.SEND_REVOKE_NOTICE, KafkaTopics.AUDIO_SHARE_EMAIL);
        EVENT_TYPE_TO_TOPIC.put(OutboxEventTypes.ROOM_LIFECYCLE_ENDED, KafkaTopics.LIVEROOM_LIFECYCLE);
    }

    public String resolveTopic(String eventType) {
        String topic = EVENT_TYPE_TO_TOPIC.get(eventType);
        if (topic == null) {
            throw new IllegalArgumentException("Unknown outbox eventType: " + eventType);
        }
        return topic;
    }
}