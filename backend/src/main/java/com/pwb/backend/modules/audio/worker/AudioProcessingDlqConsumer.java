package com.pwb.backend.modules.audio.worker;

import com.pwb.backend.common.kafka.constant.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Slf4j
public class AudioProcessingDlqConsumer {

    @KafkaListener(topics = KafkaTopics.AUDIO_PROCESSING_EVENTS_DLQ,
            groupId = "${app.audio.worker.dlq-group-id:audio-worker-dlq}",
            containerFactory = "audioProcessingKafkaListenerContainerFactory")
    public void onDeadLetter(String payload) {
        log.error("AUDIO_PROCESSING_DLQ_RECEIVED payload={}", payload);
    }
}