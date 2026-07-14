package com.pwb.backend.modules.audio.worker;

import com.pwb.backend.modules.audio.enums.AudioJobStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.modules.audio.entity.AudioProcessingJob;
import com.pwb.backend.modules.audio.event.AudioProcessingEvent;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.AudioProcessingJobRepository;
import com.pwb.backend.modules.audio.service.AudioProcessingService;
import com.pwb.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingConsumer {

    private final ObjectMapper objectMapper;
    private final AudioProcessingService audioProcessingService;
    private final AudioProcessingJobRepository jobRepository;

    @KafkaListener(topics = KafkaTopics.AUDIO_PROCESSING_EVENTS,
            groupId = "${app.audio.worker.group-id:audio-worker}",
            containerFactory = "audioProcessingKafkaListenerContainerFactory")
    public void onEvent(@Payload String payload,
                        @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
                        @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
                        Acknowledgment ack) {
        AudioProcessingEvent event;
        try {
            event = objectMapper.readValue(payload, AudioProcessingEvent.class);
        } catch (Exception ex) {
            log.error("AUDIO_EVENT_DESERIALIZE_FAILED partition={} offset={} reason={}",
                    partition, offset, ex.getMessage());
            ack.acknowledge();
            return;
        }

        try {
            audioProcessingService.process(event);
            ack.acknowledge();
        } catch (Exception ex) {
            int attempt = recordAttempt(event.demoId(), ex.getMessage());
            if (attempt >= AudioProcessingJob.MAX_ATTEMPTS) {
                log.error("AUDIO_EVENT_EXHAUSTED demoId={} attempts={} reason={}",
                        event.demoId(), attempt, ex.getMessage());
                throw ex;
            }
            log.warn("AUDIO_EVENT_RETRY demoId={} attempt={}/{} reason={}",
                    event.demoId(), attempt, AudioProcessingJob.MAX_ATTEMPTS, ex.getMessage());
            throw ex;
        }
    }

    private int recordAttempt(UUID demoId, String reason) {
        return jobRepository.findByDemoId(demoId).map(job -> {
            job.incrementAttempt(truncate(reason));
            if (job.getAttemptCount() >= AudioProcessingJob.MAX_ATTEMPTS) {
                job.setStatus(AudioJobStatus.FAILED);
                job.setCompletedAt(Instant.now());
            }
            jobRepository.save(job);
            return job.getAttemptCount();
        }).orElse(1);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1024 ? value.substring(0, 1024) : value;
    }

    @SuppressWarnings("unused")
    private void propagate(BusinessException ex) {
        throw ex;
    }

    @SuppressWarnings("unused")
    private void swallowUnreferenced() {
        AudioErrorCode.DEMO_AUDIO_PROCESSING_FAILED.httpStatus();
    }
}
