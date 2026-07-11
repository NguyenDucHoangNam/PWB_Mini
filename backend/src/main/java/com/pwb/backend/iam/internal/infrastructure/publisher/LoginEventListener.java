package com.pwb.backend.iam.internal.infrastructure.publisher;

import com.pwb.backend.iam.api.event.LoginSuccessEvent;
import com.pwb.backend.iam.internal.interfaces.config.IamProperties;
import com.pwb.backend.iam.internal.application.helper.SessionMetadataBuilder;
import com.pwb.backend.iam.internal.application.helper.UserAgentParser;
import com.pwb.backend.iam.internal.infrastructure.repository.UserRepository;
import com.pwb.backend.iam.internal.application.service.GeoIpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles successful login events on a dedicated async thread pool.
 *
 * <p>Ordering note: {@code @Async @EventListener} runs on a separate thread
 * from the login request, so the transaction started for the login response
 * is NOT inherited. {@link LoginAnomalyService} opens its own transaction,
 * and any outbox events it creates will commit asynchronously — possibly
 * arriving at the user BEFORE or AFTER the login response itself. This is
 * intentional: we do not want GeoIP lookups or anomaly detection to delay
 * the login response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventListener {

  private static final String LAST_LOGIN_KEY_PREFIX = "user:last_login:";

  private final GeoIpService geoIpService;
  private final StringRedisTemplate redisTemplate;
  private final UserRepository userRepository;
  private final LoginAnomalyService loginAnomalyService;
  private final IamProperties iamProperties;
  private final SessionMetadataBuilder sessionMetadataBuilder;

  @Async
  @EventListener
  public void handleLoginSuccess(LoginSuccessEvent event) {
    String userId = event.getUserId();
    String ipAddress = event.getIpAddress();
    String userAgent = event.getUserAgent();
    String newLocation = geoIpService.getLocation(ipAddress);
    String redisKey = LAST_LOGIN_KEY_PREFIX + userId;

    String browser = UserAgentParser.detectBrowser(userAgent);
    String os = UserAgentParser.detectOs(userAgent);
    String device = sessionMetadataBuilder.formatDevice(browser, os);

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

      boolean deviceChanged = oldDevice != null && !oldDevice.equals(device);

      if (locationChanged || deviceChanged) {
        log.warn("Anomalous login detected for user {}: Location changed ({} -> {}) or Device changed",
            userId, oldLocation, newLocation);

        userRepository.findById(userId).ifPresent(user ->
            loginAnomalyService.recordAnomalousLogin(
                user.getId(), user.getEmail(), user.getFullName(),
                ipAddress, newLocation, device));
      }
    }

    Map<String, String> newLoginData = Map.of(
        "ip", ipAddress,
        "location", newLocation,
        "device", device,
        "timestamp", Instant.now().toString()
    );

    redisTemplate.opsForHash().putAll(redisKey, newLoginData);
    redisTemplate.expire(redisKey, iamProperties.getLoginAnomaly().getLastLoginCacheDays(), TimeUnit.DAYS);
  }
}
