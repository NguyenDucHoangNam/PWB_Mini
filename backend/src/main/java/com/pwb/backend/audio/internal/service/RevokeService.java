package com.pwb.backend.audio.internal.service;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.helper.IpHashService;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.model.DemoDistribution;
import com.pwb.backend.audio.internal.model.DemoRevokeAudit;
import com.pwb.backend.audio.internal.repository.DemoDistributionRepository;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.audio.internal.repository.DemoRevokeAuditRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeService {

  private final DemoRepository demoRepository;
  private final DemoDistributionRepository distributionRepository;
  private final DemoRevokeAuditRepository auditRepository;
  private final DistributionCacheService distributionCacheService;
  private final StreamSessionService streamSessionService;
  private final AesKeyCacheService aesKeyCacheService;
  private final IpHashService ipHashService;
  private final AudioProperties audioProperties;
  private final StringRedisTemplate redisTemplate;

  @Autowired(required = false)
  private SimpMessagingTemplate simpMessagingTemplate;
  @Autowired(required = false)
  private ApplicationEventPublisher applicationEventPublisher;

  public boolean isAlreadyRevoked(String distributionId) {
    String key = "revoked:completed:" + distributionId;
    Boolean exists = redisTemplate.hasKey(key);
    return Boolean.TRUE.equals(exists);
  }

  @Transactional
  public void revoke(String distributionId, String producerId, HttpServletRequest request) {
    if (isAlreadyRevoked(distributionId)) {
      log.info("REVOKE_IDEMPOTENT_SKIP distributionId={} userId={}", distributionId, producerId);
      writeAudit(distributionId, "unknown", producerId, "manual", request);
      return;
    }

    Optional<DemoDistribution> opt = distributionRepository.findByIdAndDeletedFalse(distributionId);
    if (opt.isEmpty()) {
      log.info("REVOKE_IDEMPOTENT_NOT_FOUND distributionId={} userId={}", distributionId, producerId);
      writeAudit(distributionId, "unknown", producerId, "not-found-revoked", request);
      markIdempotent(distributionId);
      return;
    }

    DemoDistribution distribution = opt.get();
    Demo demo = demoRepository.findById(distribution.getDemoId())
        .orElseThrow(() -> new BusinessException(ErrorCode.DEMO_NOT_FOUND, "Demo not found"));
    if (!producerId.equals(demo.getOwnerId())) {
      writeAudit(distributionId, distribution.getDemoId(), producerId, "unauthorized", request);
      throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "Caller is not the owner of this demo");
    }

    if (distribution.isRevoked()) {
      log.info("REVOKE_IDEMPOTENT_SKIP distributionId={} alreadyRevoked=true", distributionId);
      writeAudit(distributionId, distribution.getDemoId(), producerId, "already-revoked", request);
      markIdempotent(distributionId);
      return;
    }

    Instant now = Instant.now();
    distribution.setRevoked(true);
    distribution.setRevokedAt(now);
    distribution.setRevokeReason("manual");
    distributionRepository.save(distribution);

    String shareToken = distribution.getShareToken().toString();
    distributionCacheService.evict(shareToken);
    distributionCacheService.markRevoked(shareToken);

    int jtiCount = streamSessionService.blacklistAllJtisForShareToken(shareToken,
        Duration.ofSeconds(audioProperties.getStreamSecureCookie().getTtlSeconds()));

    boolean isOnlyActiveDistribution = distributionRepository.findActiveByDemoId(distribution.getDemoId())
        .stream()
        .filter(d -> !d.getId().equals(distributionId))
        .count() == 0;
    if (isOnlyActiveDistribution) {
      aesKeyCacheService.evictForRevoke(distribution.getDemoId());
    }

    broadcastRevoked(distribution.getThreadId(), shareToken);
    writeAudit(distributionId, distribution.getDemoId(), producerId, "manual", request);

    markIdempotent(distributionId);

    log.info("DISTRIBUTION_REVOKED distributionId={} token={} jtiCount={}",
        distributionId, shareToken, jtiCount);
  }

  @Transactional
  public void revokeCascadeForDeletedDemo(String demoId, String producerId) {
    List<DemoDistribution> active = distributionRepository.findActiveByDemoId(demoId);
    for (DemoDistribution d : active) {
      Instant now = Instant.now();
      d.setRevoked(true);
      d.setRevokedAt(now);
      d.setRevokeReason("demo-deleted");
      distributionRepository.save(d);

      String shareToken = d.getShareToken().toString();
      distributionCacheService.evict(shareToken);
      distributionCacheService.markRevoked(shareToken);
      streamSessionService.blacklistAllJtisForShareToken(shareToken,
          Duration.ofSeconds(audioProperties.getStreamSecureCookie().getTtlSeconds()));
      broadcastRevoked(d.getThreadId(), shareToken);

      DemoRevokeAudit audit = new DemoRevokeAudit();
      audit.setDistributionId(d.getId());
      audit.setDemoId(demoId);
      audit.setRevokedByUserId(producerId);
      audit.setReason("demo-deleted");
      audit.setRevokedAt(now);
      auditRepository.save(audit);
    }
    aesKeyCacheService.evictForRevoke(demoId);
  }

  private void markIdempotent(String distributionId) {
    int ttl = audioProperties.getRevoke().getIdempotencyTtlSeconds();
    redisTemplate.opsForValue().set("revoked:completed:" + distributionId, "1",
        Duration.ofSeconds(ttl));
  }

  private void writeAudit(String distributionId, String demoId, String userId, String reason,
                          HttpServletRequest request) {
    DemoRevokeAudit audit = new DemoRevokeAudit();
    audit.setDistributionId(distributionId);
    audit.setDemoId(demoId);
    audit.setRevokedByUserId(userId);
    audit.setReason(reason);
    if (request != null) {
      audit.setIpSubnetHash(ipHashService.hashSubnetV4(clientIp(request)));
      String ua = request.getHeader("User-Agent");
      if (ua != null) {
        audit.setUserAgent(ua.length() > 250 ? ua.substring(0, 250) : ua);
      }
    }
    auditRepository.save(audit);
    log.info("REVOKE_AUDIT_RECORDED distributionId={} reason={}", distributionId, reason);
  }

  private void broadcastRevoked(String threadId, String shareToken) {
    if (!audioProperties.getRevoke().isNotifyListenerWebsocket()) {
      return;
    }
    if (simpMessagingTemplate == null) {
      log.warn("WS_BROADCAST_FAILED reason=missing_simp_messaging_template");
      return;
    }
    String destination = audioProperties.getRevoke().getDistributionRevokedQueue();
    try {
      java.util.Map<String, Object> payload = java.util.Map.of(
          "event", "DISTRIBUTION_REVOKED",
          "data", java.util.Map.of(
              "shareToken", shareToken,
              "threadId", threadId == null ? "" : threadId,
              "timestamp", Instant.now().toString()));
      simpMessagingTemplate.convertAndSend((String) destination, (Object) payload);
      log.info("DISTRIBUTION_REVOKED_WS_SENT topic={} shareToken={}", destination, shareToken);
    } catch (Exception ex) {
      log.warn("WS_BROADCAST_FAILED topic={} error={}", destination, ex.getMessage());
    }
  }

  private String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return request.getRemoteAddr();
  }
}