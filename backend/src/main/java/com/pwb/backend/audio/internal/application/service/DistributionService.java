package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.api.dto.request.DistributeDemoRequest;
import com.pwb.backend.audio.api.dto.response.DistributeDemoResponse;
import com.pwb.backend.audio.api.dto.response.DistributionListItem;
import com.pwb.backend.audio.api.dto.response.DistributionListResponse;
import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.domain.enums.DemoStatus;
import com.pwb.backend.audio.internal.domain.model.Demo;
import com.pwb.backend.audio.internal.domain.model.DemoDistribution;
import com.pwb.backend.audio.internal.domain.model.SharedThread;
import com.pwb.backend.audio.internal.application.factory.AudioOutboxEventFactory;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoDistributionRepository;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoRepository;
import com.pwb.backend.audio.internal.infrastructure.repository.SharedThreadRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionService {

  private final DemoRepository demoRepository;
  private final SharedThreadRepository sharedThreadRepository;
  private final DemoDistributionRepository distributionRepository;
  private final EmailDomainBlacklistService blacklistService;
  private final ShareQuotaService shareQuotaService;
  private final AudioOutboxEventFactory outboxFactory;
  private final AudioProperties audioProperties;

  @Transactional
  public DistributeDemoResponse distribute(String producerId, String demoId, DistributeDemoRequest request) {
    Demo demo = demoRepository.findByIdAndOwnerIdAndDeletedFalse(demoId, producerId)
        .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND, "Demo not found"));
    if (demo.getStatus() != DemoStatus.ACTIVE) {
      throw new BusinessException(AudioErrorCode.DEMO_NOT_ACTIVE,
          "Demo must be ACTIVE before distribution");
    }

    String normalizedEmail = request.recipientEmail().trim().toLowerCase(Locale.ROOT);
    blacklistService.validate(normalizedEmail);
    shareQuotaService.checkAndIncrement(producerId, normalizedEmail);

    String emailHash = sha256(normalizedEmail);
    SharedThread thread = sharedThreadRepository
        .findByProducerAndHash(producerId, emailHash)
        .orElseGet(() -> createThread(producerId, normalizedEmail, emailHash));
    thread.setLastInteractedAt(Instant.now());
    sharedThreadRepository.save(thread);

    UUID shareToken = UUID.randomUUID();
    DemoDistribution distribution = new DemoDistribution();
    distribution.setThreadId(thread.getId());
    distribution.setDemoId(demoId);
    distribution.setRecipientEmail(normalizedEmail);
    distribution.setShareToken(shareToken);
    distribution.setAllowDownload(Boolean.TRUE.equals(request.allowDownload()));
    distribution.setRevoked(false);
    distribution.setPlayCount(0);
    distribution = distributionRepository.save(distribution);
    log.info("DISTRIBUTION_REGISTERED distId={} token={}", distribution.getId(), shareToken);

    String baseUrl = audioProperties.getDistribution().getShareLinkBaseUrl();
    String shareLink = baseUrl.endsWith("/")
        ? baseUrl + "shared/" + shareToken
        : baseUrl + "/shared/" + shareToken;

    Map<String, Object> payload = new HashMap<>();
    payload.put("eventType", AudioOutboxEventFactory.EVENT_TYPE_SEND_SHARE_EMAIL);
    payload.put("email", normalizedEmail);
    payload.put("fullName", "Producer");
    payload.put("distributionId", distribution.getId());
    payload.put("shareToken", shareToken.toString());
    payload.put("shareLink", shareLink);
    payload.put("demoTitle", demo.getTitle());
    payload.put("allowDownload", distribution.isAllowDownload());
    payload.put("locale", "vi");
    String idempotencyKey = "SEND_SHARE_EMAIL:" + distribution.getId();
    outboxFactory.create(AudioOutboxEventFactory.EVENT_TYPE_SEND_SHARE_EMAIL,
        distribution.getId(), payload, idempotencyKey);
    log.info("OUTBOX_EMAIL_QUEUED aggregateId={} type=SEND_SHARE_EMAIL", distribution.getId());

    return new DistributeDemoResponse(
        distribution.getId(),
        thread.getId(),
        shareToken,
        normalizedEmail,
        distribution.isAllowDownload(),
        shareLink
    );
  }

  @Transactional(readOnly = true)
  public DistributionListResponse listDistributions(String producerId, String demoId,
                                                     int page, int size, boolean includeRevoked) {
    Demo demo = demoRepository.findByIdAndOwnerIdAndDeletedFalse(demoId, producerId)
        .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND, "Demo not found"));
    if (size < 1) size = 20;
    if (size > 100) size = 100;
    if (page < 0) page = 0;
    Pageable pageable = PageRequest.of(page, size);
    Page<DemoDistribution> result = includeRevoked
        ? distributionRepository.findAllByDemo(demoId, pageable)
        : distributionRepository.findActiveByDemo(demoId, pageable);
    java.util.List<DistributionListItem> items = result.getContent().stream()
        .map(d -> new DistributionListItem(
            d.getId(),
            d.getThreadId(),
            d.getShareToken(),
            d.getRecipientEmail(),
            d.isAllowDownload(),
            d.isRevoked(),
            d.getPlayCount(),
            d.getLastPlayedAt(),
            d.getCreatedAt()
        ))
        .toList();
    log.info("DISTRIBUTION_LIST_PAGE demoId={} page={} size={} totalElements={}",
        demoId, page, size, result.getTotalElements());
    return new DistributionListResponse(
        items, page, size, result.getTotalElements(), result.getTotalPages(), result.hasNext());
  }

  private SharedThread createThread(String producerId, String email, String hash) {
    SharedThread thread = new SharedThread();
    thread.setProducerId(producerId);
    thread.setRecipientEmail(email);
    thread.setRecipientEmailHash(hash);
    thread.setLastInteractedAt(Instant.now());
    SharedThread saved = sharedThreadRepository.save(thread);
    log.info("SHARED_THREAD_CREATED threadId={} producerId={}", saved.getId(), producerId);
    return saved;
  }

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
