package com.pwb.backend.iam.internal.factory;

import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.model.IamOutboxEvent;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.IamOutboxEventRepository;
import com.pwb.backend.shared.outbox.factory.AbstractOutboxEventFactory;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class OutboxEventFactory extends AbstractOutboxEventFactory {

  public static final String AGGREGATE_TYPE_IAM = "IAM";
  public static final String EVENT_TYPE_REGISTRATION_OTP = "REGISTRATION_OTP";
  public static final String EVENT_TYPE_WELCOME_EMAIL = "WELCOME_EMAIL";
  public static final String EVENT_TYPE_PASSWORD_RESET = "PASSWORD_RESET";
  public static final String EVENT_TYPE_ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
  public static final String EVENT_TYPE_ACCOUNT_DELETION_CANCELLED = "ACCOUNT_DELETION_CANCELLED";
  public static final String EVENT_TYPE_ACCOUNT_ANONYMIZED = "ACCOUNT_ANONYMIZED";
  public static final String EVENT_TYPE_ANOMALOUS_LOGIN = "ANOMALOUS_LOGIN";

  private final IamOutboxEventRepository repository;

  public OutboxEventFactory(
      IamOutboxEventRepository repository,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper,
      org.springframework.context.ApplicationEventPublisher eventPublisher,
      com.pwb.backend.shared.outbox.cipher.OutboxPayloadCipher cipher
  ) {
    super(objectMapper, cipher, eventPublisher);
    this.repository = repository;
  }

  @Override
  protected String getAggregateType() {
    return AGGREGATE_TYPE_IAM;
  }

  @Override
  protected void onEventCreated(OutboxEvent event) {
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public IamOutboxEvent createAndPublish(String eventType, String aggregateId, Map<String, Object> payload) {
    return createAndPublish(eventType, aggregateId, payload, java.util.UUID.randomUUID().toString());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public IamOutboxEvent createAndPublish(String eventType, String aggregateId,
                                          Map<String, Object> payload, String idempotencyKey) {
    IamOutboxEvent event = new IamOutboxEvent();
    IamOutboxEvent saved = createEvent(event, eventType, aggregateId, payload, idempotencyKey);
    repository.save(saved);
    publishOutboxCreatedEvent(saved.getId());
    return saved;
  }

  private String businessKey(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append(':');
      sb.append(parts[i] == null ? "" : parts[i]);
    }
    return sb.toString();
  }

  public IamOutboxEvent registrationOtp(User user, String email, String otpCode,
                                        String fullName, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_REGISTRATION_OTP);
    payload.put("email", email);
    payload.put("otpCode", otpCode);
    payload.put("fullName", fullName == null ? "" : fullName);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_REGISTRATION_OTP, user.getId(), payload,
        businessKey("REGISTRATION_OTP", user.getId(), email));
  }

  public IamOutboxEvent welcomeEmail(User user, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_WELCOME_EMAIL);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_WELCOME_EMAIL, user.getId(), payload,
        businessKey("WELCOME_EMAIL", user.getId(), user.getEmail()));
  }

  public IamOutboxEvent passwordReset(User user, String token, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_PASSWORD_RESET);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("token", token);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_PASSWORD_RESET, user.getId(), payload,
        businessKey("PASSWORD_RESET", user.getId(), user.getEmail(), String.valueOf(System.currentTimeMillis())));
  }

  public IamOutboxEvent accountDeletionRequested(User user, String deletionDate, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_DELETION_REQUESTED);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("deletionDate", deletionDate);
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_DELETION_REQUESTED, user.getId(), payload,
        businessKey("ACCOUNT_DELETION_REQUESTED", user.getId()));
  }

  public IamOutboxEvent accountAnonymized(String userId, String anonymizedEmail, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_ANONYMIZED);
    payload.put("userId", userId);
    payload.put("email", anonymizedEmail);
    payload.put("status", "ANONYMIZED");
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_ANONYMIZED, userId, payload,
        businessKey("ACCOUNT_ANONYMIZED", userId, anonymizedEmail));
  }

  public IamOutboxEvent accountDeletionCancelled(User user, String locale) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ACCOUNT_DELETION_CANCELLED);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("status", user.getStatus() == null ? "ACTIVE" : user.getStatus().name());
    payload.put("locale", locale);
    return createAndPublish(EVENT_TYPE_ACCOUNT_DELETION_CANCELLED, user.getId(), payload,
        businessKey("ACCOUNT_DELETION_CANCELLED", user.getId(), user.getEmail()));
  }

  public IamOutboxEvent anomalousLogin(User user, String ip, String location, String device) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", EVENT_TYPE_ANOMALOUS_LOGIN);
    payload.put("email", user.getEmail());
    payload.put("fullName", user.getFullName() == null ? "" : user.getFullName());
    payload.put("userId", user.getId());
    payload.put("ip", ip);
    payload.put("location", location == null ? "" : location);
    payload.put("device", device == null ? "" : device);
    payload.put("timestamp", java.time.Instant.now().toString());
    return createAndPublish(EVENT_TYPE_ANOMALOUS_LOGIN, user.getId(), payload,
        businessKey("ANOMALOUS_LOGIN", user.getId(), ip, String.valueOf(System.currentTimeMillis())));
  }
}
