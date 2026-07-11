package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.application.helper.IpHashService;
import com.pwb.backend.audio.internal.domain.model.DemoDistribution;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoDistributionRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackPlayService {

  private final StringRedisTemplate redisTemplate;
  private final DemoDistributionRepository distributionRepository;
  private final IpHashService ipHashService;
  private final AudioProperties audioProperties;

  @Transactional
  public void recordPlay(String shareToken, HttpServletRequest request) {
    String ip = clientIp(request);
    String sessionId = computeSessionId(request, null);
    String sessionKey = "play_session:" + shareToken + ":" + sessionId;

    Boolean firstPlay = redisTemplate.opsForValue().setIfAbsent(
        sessionKey, "1", Duration.ofSeconds(audioProperties.getPlay().getAntiFraudWindowSeconds()));
    if (Boolean.FALSE.equals(firstPlay)) {
      log.info("PLAY_COUNT_SPAM_BLOCKED token={} sessionId={}", shareToken, sessionId);
      return;
    }
    redisTemplate.expire(sessionKey, Duration.ofHours(24));

    DemoDistribution distribution = distributionRepository.findByShareToken(UUID.fromString(shareToken))
        .orElseThrow(() -> new BusinessException(AudioErrorCode.LINK_NOT_FOUND, "Distribution not found"));

    distribution.setPlayCount(distribution.getPlayCount() + 1);
    distribution.setLastPlayedAt(Instant.now());
    distributionRepository.save(distribution);

    log.info("PLAY_COUNT_INCREMENTED token={} sessionId={} algorithm={}",
        shareToken, sessionId, audioProperties.getPlay().getFingerprintAlgorithm());
  }

  public String computeSessionId(HttpServletRequest request, String cookieJti) {
    String ip = clientIp(request);
    String ua = header(request, "User-Agent");
    String acceptLang = header(request, "Accept-Language");
    String chUa = header(request, "Sec-CH-UA");
    String chPlatform = header(request, "Sec-CH-UA-Platform");
    StringBuilder sb = new StringBuilder();
    sb.append(ip == null ? "" : ip).append('|');
    sb.append(chUa == null ? "" : chUa).append('|');
    sb.append(chPlatform == null ? "" : chPlatform).append('|');
    sb.append(ua == null ? "" : ua).append('|');
    sb.append(acceptLang == null ? "" : acceptLang).append('|');
    sb.append(cookieJti == null ? "" : cookieJti);
    return ipHashService.hash("session", sb.toString());
  }

  private String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return request.getRemoteAddr();
  }

  private String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null ? null : value;
  }
}