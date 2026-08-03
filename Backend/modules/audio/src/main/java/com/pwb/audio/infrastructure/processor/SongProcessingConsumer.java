package com.pwb.audio.infrastructure.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.audio.infrastructure.processor.event.SongProcessingRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pwb.audio.processor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class SongProcessingConsumer {

    private final SongProcessorWorker songProcessorWorker;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${pwb.audio.processor.kafka.topic:voice.processing.v1}",
            groupId = "${pwb.audio.processor.kafka.group-id:audio-song-processor}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSongProcessingRequested(ConsumerRecord<String, String> record) {
        try {
            SongProcessingRequested event = objectMapper.readValue(record.value(), SongProcessingRequested.class);
            log.debug("Received song processing request: songId={}", event.songId());
            songProcessorWorker.process(event.songId());
        } catch (Exception ex) {
            log.error("Song processing consumer failed: key={}", record.key(), ex);
            throw new IllegalStateException("Song processing failed for key " + record.key(), ex);
        }
    }
}