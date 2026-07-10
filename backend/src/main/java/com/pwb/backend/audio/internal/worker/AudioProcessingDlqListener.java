package com.pwb.backend.audio.internal.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AudioProcessingDlqListener {

  @KafkaListener(
      topics = "audio-processing-events-dlq",
      groupId = "${spring.kafka.consumer.group-id.audio-dlq:pwb-audio-worker-dlq}",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void consumeDlq(@Payload String payload,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                         @Header(KafkaHeaders.OFFSET) long offset) {
    log.error("DLQ message on topic={}, partition={}, offset={}, payload={}",
        topic, partition, offset, payload);
  }
}
