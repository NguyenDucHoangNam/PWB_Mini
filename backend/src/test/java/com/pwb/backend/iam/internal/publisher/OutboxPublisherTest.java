package com.pwb.backend.iam.internal.publisher;

import com.pwb.backend.iam.internal.config.IamProperties;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.factory.OutboxEventFactory;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

  private OutboxEventRepository outboxEventRepository;
  private KafkaTemplate<String, String> kafkaTemplate;
  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    outboxEventRepository = mock(OutboxEventRepository.class);
    kafkaTemplate = mock(KafkaTemplate.class);
    IamProperties props = new IamProperties();
    props.getOutbox().setDeadLetterAfterRetries(3);
    publisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, new OutboxPayloadCipher(""), props);
  }

  @Test
  void processOutboxEvent_success_marksProcessed() {
    OutboxEvent event = newEvent(OutboxEventFactory.EVENT_TYPE_REGISTRATION_OTP);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    publisher.processOutboxEvent(event);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
    OutboxEvent last = lastSaved(captor);
    assertEquals(OutboxEventStatus.PROCESSED, last.getStatus());
    assertNull(last.getLastError());
  }

  @Test
  void processOutboxEvent_failureUnderThreshold_marksFailed() {
    OutboxEvent event = newEvent(OutboxEventFactory.EVENT_TYPE_REGISTRATION_OTP);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

    publisher.processOutboxEvent(event);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
    OutboxEvent last = lastSaved(captor);
    assertEquals(OutboxEventStatus.FAILED, last.getStatus());
    assertEquals(1, last.getRetryCount());
    assertNotNull(last.getLastError());
    assertTrue(last.getLastError().contains("kafka down"));
  }

  @Test
  void processOutboxEvent_failureAtThreshold_marksDeadLettered() {
    OutboxEvent event = newEvent(OutboxEventFactory.EVENT_TYPE_REGISTRATION_OTP);
    event.setRetryCount(2);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("permanent")));

    publisher.processOutboxEvent(event);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
    OutboxEvent last = lastSaved(captor);
    assertEquals(OutboxEventStatus.DEAD_LETTERED, last.getStatus());
    assertEquals(3, last.getRetryCount());
    assertNotNull(last.getDeadLetteredAt());
  }

  @Test
  void processOutboxEvent_accountAnonymized_routedToDifferentTopic() {
    OutboxEvent event = newEvent(OutboxEventFactory.EVENT_TYPE_ACCOUNT_ANONYMIZED);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    publisher.processOutboxEvent(event);

    verify(kafkaTemplate).send(eq("iam-account-events"), anyString(), anyString());
  }

  @Test
  void processOutboxEvent_notificationEvent_routedToNotificationTopic() {
    OutboxEvent event = newEvent(OutboxEventFactory.EVENT_TYPE_REGISTRATION_OTP);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    publisher.processOutboxEvent(event);

    verify(kafkaTemplate).send(eq("notification-events"), anyString(), anyString());
  }

  private static OutboxEvent lastSaved(ArgumentCaptor<OutboxEvent> captor) {
    return captor.getAllValues().get(captor.getAllValues().size() - 1);
  }

  private static OutboxEvent newEvent(String type) {
    OutboxEvent e = new OutboxEvent();
    e.setId("outbox-id");
    e.setAggregateType("IAM");
    e.setAggregateId("user-uuid");
    e.setEventType(type);
    e.setPayload("{\"k\":\"v\"}");
    return e;
  }
}