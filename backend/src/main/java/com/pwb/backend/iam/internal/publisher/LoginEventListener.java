package com.pwb.backend.iam.internal.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.api.event.OutboxCreatedEvent;
import com.pwb.backend.iam.internal.enums.OutboxEventStatus;
import com.pwb.backend.iam.internal.model.OutboxEvent;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.OutboxEventRepository;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.iam.internal.service.GeoIpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventListener {

  private static final String LAST_LOGIN_KEY_PREFIX = "user:last_login:";
  private static final String AGGREGATE_TYPE_IAM = "IAM";
  private static final String EVENT_TYPE_ANOMALOUS_LOGIN = "ANOMALOUS_LOGIN";

  private final GeoIpService geoIpService;
  private final StringRedisTemplate redisTemplate;
  private final OutboxEventRepository outboxEventRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  @Async
  @EventListener
  public void handleLoginSuccess(LoginSuccessEvent event) {
    String userId = event.getUserId();
    String ipAddress = event.getIpAddress();
    String userAgent = event.getUserAgent();
    String newLocation = geoIpService.getLocation(ipAddress);
    String redisKey = LAST_LOGIN_KEY_PREFIX + userId;

    Map<Object, Object> lastLoginData = redisTemplate.opsForHash().entries(redisKey);

    if (!lastLoginData.isEmpty()) {
      String oldLocation = (String) lastLoginData.get("location");
      String oldDevice = (String) lastLoginData.get("device");

      boolean locationChanged = oldLocation != null 
          && !oldLocation.equals(newLocation) 
          && !"Unknown".equals(newLocation) 
          && !"Local / Unknown".equals(newLocation)
          && !"Unknown".equals(oldLocation) 
          && !"Local / Unknown".equals(oldLocation);
          
      boolean deviceChanged = oldDevice != null && !oldDevice.equals(userAgent);

      if (locationChanged || deviceChanged) {
        log.warn("Anomalous login detected for user {}: Location changed ({} -> {}) or Device changed",
            userId, oldLocation, newLocation);
        
        userRepository.findById(userId).ifPresent(user -> 
            createAnomalousLoginOutbox(user, ipAddress, newLocation, userAgent)
        );
      }
    }

    Map<String, String> newLoginData = Map.of(
        "ip", ipAddress,
        "location", newLocation,
        "device", userAgent,
        "timestamp", Instant.now().toString()
    );

    redisTemplate.opsForHash().putAll(redisKey, newLoginData);
    redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
  }

  private void createAnomalousLoginOutbox(User user, String ip, String location, String device) {
    try {
      Map<String, Object> payloadMap = new HashMap<>();
      payloadMap.put("eventType", EVENT_TYPE_ANOMALOUS_LOGIN);
      payloadMap.put("email", user.getEmail());
      payloadMap.put("fullName", user.getFullName() != null ? user.getFullName() : "");
      payloadMap.put("userId", user.getId());
      payloadMap.put("ip", ip);
      payloadMap.put("location", location != null ? location : "");
      payloadMap.put("device", device != null ? device : "");
      payloadMap.put("timestamp", Instant.now().toString());

      String payload = objectMapper.writeValueAsString(payloadMap);

      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setAggregateType(AGGREGATE_TYPE_IAM);
      outboxEvent.setAggregateId(user.getId());
      outboxEvent.setEventType(EVENT_TYPE_ANOMALOUS_LOGIN);
      outboxEvent.setIdempotencyKey(UUID.randomUUID().toString());
      outboxEvent.setPayload(payload);
      outboxEvent.setStatus(OutboxEventStatus.PENDING);

      OutboxEvent saved = outboxEventRepository.save(outboxEvent);
      eventPublisher.publishEvent(new OutboxCreatedEvent(saved.getId()));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize anomalous login event payload", e);
    }
  }
}
