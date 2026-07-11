package com.pwb.backend.iam.internal.infrastructure.publisher;

import com.pwb.backend.iam.internal.domain.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.infrastructure.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.messaging.outbox.cipher.OutboxPayloadCipher;
import com.pwb.backend.shared.messaging.outbox.processor.AbstractOutboxPublisher;
import com.pwb.backend.shared.messaging.outbox.service.OutboxService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Component
public class IamOutboxPublisher extends AbstractOutboxPublisher<IamOutboxEvent> {

  public static final String NOTIFICATION_TOPIC = "notification-events";
  public static final String IAM_ACCOUNT_EVENTS_TOPIC = "iam-account-events";
  public static final String EVENT_TYPE_ACCOUNT_ANONYMIZED = "ACCOUNT_ANONYMIZED";

  private final Map<String, String> topicByEventType;

  public IamOutboxPublisher(IamOutboxEventRepository repository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            OutboxPayloadCipher cipher,
                            OutboxService outboxService,
                            PlatformTransactionManager transactionManager) {
    super(repository, kafkaTemplate, cipher, outboxService, transactionManager);
    this.topicByEventType = Map.of(
        EVENT_TYPE_ACCOUNT_ANONYMIZED, IAM_ACCOUNT_EVENTS_TOPIC
    );
  }

  @Override
  protected String defaultTopic() {
    return NOTIFICATION_TOPIC;
  }

  @Override
  protected String resolveTopic(IamOutboxEvent event) {
    return topicByEventType.getOrDefault(event.getEventType(), defaultTopic());
  }
}