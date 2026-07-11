package com.pwb.backend.audio.internal.infrastructure.publisher;

import com.pwb.backend.audio.internal.domain.model.AudioOutboxEvent;
import com.pwb.backend.audio.internal.infrastructure.repository.AudioOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.messaging.outbox.processor.AbstractOutboxPublisher;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class AudioOutboxPublisher extends AbstractOutboxPublisher<AudioOutboxEvent> {

  public static final String AUDIO_TOPIC = "notification-events";

  public AudioOutboxPublisher(AudioOutboxEventRepository repository,
                              KafkaTemplate<String, String> kafkaTemplate,
                              OutboxPayloadCipher cipher,
                              OutboxService outboxService,
                              PlatformTransactionManager transactionManager) {
    super(repository, kafkaTemplate, cipher, outboxService, transactionManager);
  }

  @Override
  protected String defaultTopic() {
    return AUDIO_TOPIC;
  }
}
