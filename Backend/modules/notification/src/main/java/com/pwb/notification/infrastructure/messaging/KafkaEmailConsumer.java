package com.pwb.notification.infrastructure.messaging;

import com.pwb.notification.api.NotificationFacade;
import com.pwb.notification.api.event.EmailRequestedIntegrationEvent;
import com.pwb.outbox.infrastructure.messaging.OutboxKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEmailConsumer {

    private final NotificationFacade notificationFacade;

    @KafkaListener(
        topics = OutboxKafkaConfig.TOPIC_EMAIL,
        groupId = "notification-email-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(EmailRequestedIntegrationEvent event) {
        log.info("Email event received: eventId={}, template={}, to={}",
                event.eventId(), event.templateName(), event.to());
        notificationFacade.consumeEmailRequest(event);
    }
}
