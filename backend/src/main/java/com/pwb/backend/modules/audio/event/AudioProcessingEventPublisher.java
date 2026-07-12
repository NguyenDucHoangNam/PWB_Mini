package com.pwb.backend.modules.audio.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(AudioProcessingEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AudioProcessingEvent", ex);
        }
        CompletableFuture<?> future = kafkaTemplate.send(
                KafkaTopics.AUDIO_PROCESSING_EVENTS, event.demoId().toString(), payload);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("AUDIO_PROCESSING_PUBLISH_FAILED demoId={} reason={}",
                        event.demoId(), ex.getMessage());
            } else {
                log.info("AUDIO_PROCESSING_PUBLISHED demoId={} topic={}",
                        event.demoId(), KafkaTopics.AUDIO_PROCESSING_EVENTS);
            }
        });
    }
}