package com.pwb.backend.iam.internal.publisher;

import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.iam.internal.repository.UserRepository;
import com.pwb.backend.iam.internal.service.GeoIpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventListener {

  private static final String LAST_LOGIN_KEY_PREFIX = "user:last_login:";

  private final GeoIpService geoIpService;
  private final StringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final LoginAnomalyService loginAnomalyService;

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

        userRepository.findById(userId)
            .ifPresent(user -> loginAnomalyService.recordAnomalousLogin(user, ipAddress, newLocation, userAgent));
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
}