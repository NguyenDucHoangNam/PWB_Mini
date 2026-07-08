package com.pwb.backend.iam.internal.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.enums.UserStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.Role;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventFactoryTest {

  private OutboxEventRepository outboxEventRepository;
  private ApplicationEventPublisher eventPublisher;
  private OutboxEventFactory factory;

  @BeforeEach
  void setUp() {
    outboxEventRepository = mock(OutboxEventRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    factory = new OutboxEventFactory(outboxEventRepository, new ObjectMapper(), eventPublisher);
    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(inv -> {
          OutboxEvent ev = inv.getArgument(0);
          ev.setId("outbox-id");
          return ev;
        });
  }

  @Test
  void registrationOtp_persistsAndPublishesCreatedEvent() {
    User user = newUser();

    OutboxEvent saved = factory.registrationOtp(user, "test@gmail.com", "123456", "Test User", "en");

    assertNotNull(saved.getId());
    assertEquals(OutboxEventFactory.EVENT_TYPE_REGISTRATION_OTP, saved.getEventType());
    assertEquals("user-uuid", saved.getAggregateId());
    assertEquals(OutboxEventFactory.AGGREGATE_TYPE_IAM, saved.getAggregateType());
    assertEquals(OutboxEventStatus.PENDING, saved.getStatus());
    assertTrue(saved.getPayload().contains("\"otpCode\":\"123456\""));
    assertTrue(saved.getPayload().contains("\"email\":\"test@gmail.com\""));

    ArgumentCaptor<OutboxCreatedEvent> captor = ArgumentCaptor.forClass(OutboxCreatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertEquals("outbox-id", captor.getValue().outboxEventId());
  }

  @Test
  void welcomeEmail_includesUserIdAndEmail() {
    OutboxEvent saved = factory.welcomeEmail(newUser(), "vi");
    assertEquals(OutboxEventFactory.EVENT_TYPE_WELCOME_EMAIL, saved.getEventType());
    assertTrue(saved.getPayload().contains("\"email\":\"test@gmail.com\""));
    assertTrue(saved.getPayload().contains("\"userId\":\"user-uuid\""));
  }

  @Test
  void passwordReset_includesToken() {
    OutboxEvent saved = factory.passwordReset(newUser(), "reset-token", "en");
    assertEquals(OutboxEventFactory.EVENT_TYPE_PASSWORD_RESET, saved.getEventType());
    assertTrue(saved.getPayload().contains("\"token\":\"reset-token\""));
  }

  @Test
  void accountDeletionRequested_includesDeletionDate() {
    OutboxEvent saved = factory.accountDeletionRequested(newUser(), "2026-12-31", "en");
    assertEquals(OutboxEventFactory.EVENT_TYPE_ACCOUNT_DELETION_REQUESTED, saved.getEventType());
    assertTrue(saved.getPayload().contains("\"deletionDate\":\"2026-12-31\""));
  }

  @Test
  void accountAnonymized_doesNotRequireFullUser() {
    OutboxEvent saved = factory.accountAnonymized("user-uuid", "deleted_xxx@pwbmini.com", "en");
    assertEquals(OutboxEventFactory.EVENT_TYPE_ACCOUNT_ANONYMIZED, saved.getEventType());
    assertEquals("user-uuid", saved.getAggregateId());
    Map<String, Object> expected = Map.of(
        "eventType", OutboxEventFactory.EVENT_TYPE_ACCOUNT_ANONYMIZED,
        "userId", "user-uuid",
        "email", "deleted_xxx@pwbmini.com",
        "status", "ANONYMIZED",
        "locale", "en");
    expected.forEach((k, v) -> assertTrue(saved.getPayload().contains("\"" + k + "\":\"" + v + "\""),
        () -> "payload missing key " + k + " value " + v));
  }

  @Test
  void createAndPublish_setsUniqueIdempotencyKey() {
    User user = newUser();
    OutboxEvent first = factory.registrationOtp(user, "a@b.com", "1", "n", "en");
    OutboxEvent second = factory.registrationOtp(user, "a@b.com", "1", "n", "en");
    assertNotNull(first.getIdempotencyKey());
    assertNotNull(second.getIdempotencyKey());
    org.junit.jupiter.api.Assertions.assertNotEquals(first.getIdempotencyKey(), second.getIdempotencyKey());
  }

  private static User newUser() {
    User u = new User();
    u.setId("user-uuid");
    u.setEmail("test@gmail.com");
    u.setUsername("testuser");
    u.setFullName("Test User");
    Role r = new Role();
    r.setName("USER");
    u.setRole(r);
    u.setStatus(UserStatus.ACTIVE);
    return u;
  }
}