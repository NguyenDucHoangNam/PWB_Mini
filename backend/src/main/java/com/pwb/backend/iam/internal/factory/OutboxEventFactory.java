package com.pwb.backend.iam.internal.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for persisting {@link OutboxEvent} rows and notifying
 * the in-process publisher. Replaces 4+ near-identical inline snippets
 * scattered across {@code AuthService}.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

  public static final String AGGREGATE_TYPE_IAM = "IAM";
  public static final String EVENT_TYPE_REGISTRATION_OTP = "REGISTRATION_OTP";
  public static final String EVENT_TYPE_WELCOME_EMAIL = "WELCOME_EMAIL";
  public static final String EVENT_TYPE_PASSWORD_RESET = "PASSWORD_RESET";
  public static final String EVENT_TYPE_ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
  public static final String EVENT_TYPE_ACCOUNT_DELETION_CANCELLED = "ACCOUNT_DELETION_CANCELLED";
  public static final String EVENT_TYPE_ACCOUNT_ANONYMIZED = "ACCOUNT_ANONYMIZED";

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent createAndPublish(String eventType, String aggregateId, Map<String, Object> payload) {
    OutboxEvent outboxEvent = new OutboxEvent();
    outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
    outboxEvent.setAggregateId(aggregateId);
    outboxEvent.setEventType(eventType);
    outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
    try {
      outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to serialize outbox event payload");
    }
    outboxEvent.setStatus(OutboxEventStatus.PENDING);

    OutboxEvent saved = outboxEventRepository.save(outboxEvent);
    eventPublisher.publishEvent(new OutboxCreatedEvent(saved.getId()));
    return saved;
  }

  public OutboxEvent registrationOtp(User user, String email, String otpCode, String fullName, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_REGISTRATION_OTP);
    payload.put("email", email);
    payload.put("otpCode", otpCode);
    payload.put("fullName", fullName == null ? "" : fullName);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_REGISTRATION_OTP, user.getId(), payload);
  }

  public OutboxEvent welcomeEmail(User user, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_WELCOME_EMAIL);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_WELCOME_EMAIL, user.getId(), payload);
  }

  public OutboxEvent passwordReset(User user, String token, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_PASSWORD_RESET);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("token", token);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_PASSWORD_RESET, user.getId(), payload);
  }

  public OutboxEvent accountDeletionRequested(User user, String deletionDate, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_DELETION_REQUESTED);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("deletionDate", deletionDate);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_DELETION_REQUESTED, user.getId(), payload);
  }

  public OutboxEvent accountAnonymized(String userId, String anonymizedEmail, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_ANONYMIZED);
    payload.put("userId", userId);
    payload.put("email", anonymizedEmail);
    payload.put("status", "ANONYMIZED");
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_ANONYMIZED, userId, payload);
  }

  public OutboxEvent accountDeletionCancelled(User user, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_DELETION_CANCELLED);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("status", user.getStatus() == null ? "ACTIVE" : user.getStatus().name());
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_DELETION_CANCELLED, user.getId(), payload);
  }

  public OutboxEvent anomalousLogin(User user, String ip, String location, String device) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", "ANOMALOUS_LOGIN");
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("ip", ip);
    payload.put("location", location == null ? "" : location);
    payload.put("device", device == null ? "" : device);
    payload.put("timestamp", java.time.Instant.now().toString());
    return createAndPublish("ANOMALOUS_LOGIN", user.getId(), payload);
  }
}