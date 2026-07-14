package com.pwb.backend.modules.share.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.kafka.constant.KafkaTopics;
import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.event.OutboxCreatedEvent;
import com.pwb.backend.common.outbox.event.ShareEmailEvent;
import com.pwb.backend.common.outbox.publisher.OutboxEventTypes;
import com.pwb.backend.common.outbox.publisher.OutboxPayloadCipher;
import com.pwb.backend.common.outbox.repository.OutboxEventRepository;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.service.AesKeyRotationService;
import com.pwb.backend.modules.share.config.ShareProperties;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.dto.request.DistributeDemoRequest;
import com.pwb.backend.modules.share.dto.response.DistributeDemoResponse;
import com.pwb.backend.modules.share.dto.response.DistributionListItemResponse;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import com.pwb.backend.modules.share.repository.SharedThreadRepository;
import com.pwb.backend.modules.share.service.BlacklistedEmailDomainService;
import com.pwb.backend.modules.share.service.DemoDistributionCacheService;
import com.pwb.backend.modules.share.service.DemoDistributionOwnershipService;
import com.pwb.backend.modules.share.service.DemoDistributionService;
import com.pwb.backend.modules.share.service.ShareDailyQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDistributionServiceImpl implements DemoDistributionService {

    private static final String LIKE_ESCAPE_BACKSLASH = "\\\\";
    private static final String LIKE_ESCAPE_PERCENT = "\\%";
    private static final String LIKE_ESCAPE_UNDERSCORE = "\\_";
    private static final int AUTOCOMPLETE_MIN_LENGTH = 2;
    private static final int AUTOCOMPLETE_MAX_LENGTH = 50;
    private static final int AUTOCOMPLETE_DEFAULT_LIMIT = 10;

    private final DemoDistributionOwnershipService ownershipService;
    private final BlacklistedEmailDomainService blacklistedDomainService;
    private final ShareDailyQuotaService quotaService;
    private final SharedThreadRepository sharedThreadRepository;
    private final DemoDistributionRepository demoDistributionRepository;
    private final OutboxEventRepository outboxRepository;
    private final OutboxPayloadCipher outboxCipher;
    private final DemoDistributionCacheService cacheService;
    private final ShareProperties shareProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AesKeyRotationService aesKeyRotationService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public DistributeDemoResponse distribute(UUID demoId, DistributeDemoRequest request, UUID producerId) {
        log.info("SHARE_DEMO_REQUEST demoId={} producerId={} recipient={}",
                demoId, producerId, maskEmail(request.recipientEmail()));

        Demo demo = ownershipService.assertActiveAndOwned(demoId, producerId);
        String normalized = normalizeEmail(request.recipientEmail());

        if (blacklistedDomainService.isBlacklisted(normalized)) {
            log.warn("BLACKLISTED_DOMAIN_REJECTED domain={} producerId={}",
                    extractDomain(normalized), producerId);
            throw new BusinessException(ShareErrorCode.INVALID_RECIPIENT_EMAIL);
        }

        quotaService.assertWithinQuota(producerId, normalized);

        String emailHash = sha256Hex(normalized);
        UUID threadId = sharedThreadRepository.upsertThread(
                UUID.randomUUID(),
                producerId,
                normalized,
                emailHash,
                BaseEntity.SYSTEM_PRINCIPAL,
                BaseEntity.SYSTEM_PRINCIPAL);
        log.info("SHARED_THREAD_UPSERTED threadId={} producerId={}", threadId, producerId);

        UUID shareToken = UUID.randomUUID();
        UUID distributionId = UUID.randomUUID();
        DemoDistribution distribution = DemoDistribution.builder()
                .id(distributionId)
                .threadId(threadId)
                .demoId(demo.getId())
                .recipientEmail(normalized)
                .shareToken(shareToken)
                .allowDownload(Boolean.TRUE.equals(request.allowDownload()))
                .revoked(false)
                .playCount(0)
                .build();
        demoDistributionRepository.save(distribution);
        log.info("DISTRIBUTION_REGISTERED distId={} shareToken={}", distributionId, shareToken);

        Instant now = Instant.now();
        ShareEmailEvent eventPayload = new ShareEmailEvent(
                distributionId,
                threadId,
                shareToken,
                normalized,
                distribution.isAllowDownload(),
                demo.getId(),
                producerId,
                now);
        persistOutboxEvent(distributionId, shareToken, eventPayload);

        DistributionListItemResponse snapshot = DistributionListItemResponse.from(distribution);
        cacheService.put(snapshot, shareToken);

        String shareLink = shareProperties.getShareLinkBaseUrl() + shareToken;
        return new DistributeDemoResponse(
                distributionId,
                threadId,
                shareToken,
                normalized,
                distribution.isAllowDownload(),
                shareLink);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DistributionListItemResponse> listDistributions(UUID demoId, UUID producerId,
                                                                boolean includeRevoked,
                                                                Pageable pageable) {
        ownershipService.assertOwned(demoId, producerId);
        Page<DemoDistribution> page = includeRevoked
                ? demoDistributionRepository.findByDemoId(demoId, pageable)
                : demoDistributionRepository.findByDemoIdAndRevokedFalse(demoId, pageable);
        log.info("DISTRIBUTION_LIST_PAGE demoId={} page={} size={} totalElements={}",
                demoId, pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
        return page.map(DistributionListItemResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> suggestRecipients(String keyword, UUID producerId) {
        if (keyword == null || keyword.length() < AUTOCOMPLETE_MIN_LENGTH
                || keyword.length() > AUTOCOMPLETE_MAX_LENGTH) {
            return List.of();
        }
        String sanitized = sanitizeLikePattern(keyword);
        List<String> suggestions = sharedThreadRepository.findRecipientEmailSuggestions(
                producerId, sanitized, AUTOCOMPLETE_DEFAULT_LIMIT);
        if (keyword.chars().anyMatch(c -> c == '\\' || c == '%' || c == '_')) {
            log.info("SQL_LIKE_ESCAPE producerId={} rawInput={} sanitized={}",
                    producerId, keyword, sanitized);
        }
        return suggestions;
    }

    @Override
    @Transactional
    public DistributionListItemResponse revokeDistribution(UUID demoId, UUID distributionId, UUID producerId) {
        ownershipService.assertOwned(demoId, producerId);
        DemoDistribution distribution = demoDistributionRepository.findById(distributionId)
                .orElseThrow(() -> new BusinessException(ShareErrorCode.DEMO_NOT_FOUND,
                        "Distribution " + distributionId + " not found"));
        if (!distribution.getDemoId().equals(demoId)) {
            log.warn("DISTRIBUTION_REVOKE_WRONG_DEMO distId={} expectedDemo={} actualDemo={}",
                    distributionId, demoId, distribution.getDemoId());
            throw new BusinessException(ShareErrorCode.FORBIDDEN_ACCESS,
                    "Distribution does not belong to demo " + demoId);
        }
        if (!distribution.isRevoked()) {
            Instant now = Instant.now();
            distribution.revoke(now);
            demoDistributionRepository.save(distribution);
            markRedisRevoked(distribution.getShareToken(), now);
            aesKeyRotationService.revokeJtisForDistribution(distribution.getShareToken());
            log.warn("DISTRIBUTION_REVOKED distId={} shareToken={} demoId={} producerId={}",
                    distributionId, distribution.getShareToken(), demoId, producerId);
        }
        return DistributionListItemResponse.from(distribution);
    }

    @Override
    @Transactional
    public int revokeAllDistributions(UUID demoId, UUID producerId) {
        ownershipService.assertOwned(demoId, producerId);
        java.util.List<DemoDistribution> active = demoDistributionRepository
                .findByDemoIdAndRevokedFalse(demoId, PageRequest.of(0, 1000))
                .getContent();
        if (active.isEmpty()) {
            log.info("DISTRIBUTION_BULK_REVOKE_EMPTY demoId={} producerId={}", demoId, producerId);
            return 0;
        }
        Instant now = Instant.now();
        for (DemoDistribution distribution : active) {
            distribution.revoke(now);
            demoDistributionRepository.save(distribution);
            markRedisRevoked(distribution.getShareToken(), now);
            aesKeyRotationService.revokeJtisForDistribution(distribution.getShareToken());
        }
        log.warn("DISTRIBUTION_BULK_REVOKED demoId={} producerId={} count={}",
                demoId, producerId, active.size());
        return active.size();
    }

    private void markRedisRevoked(java.util.UUID shareToken, Instant now) {
        try {
            String revokedKey = ShareRedisKeys.distributionRevokedKey(shareToken);
            stringRedisTemplate.opsForValue().set(revokedKey, "1",
                    java.time.Duration.ofSeconds(shareProperties.getDistributionCacheTtlSeconds()));
            cacheService.evict(shareToken);
        } catch (Exception ex) {
            log.warn("DISTRIBUTION_REDIS_REVOKE_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
        }
    }

    private void persistOutboxEvent(UUID distributionId, UUID shareToken, ShareEmailEvent eventPayload) {
        String plaintext;
        try {
            plaintext = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize share email event payload", ex);
        }
        String encryptedPayload = outboxCipher.encrypt(plaintext);
        UUID idempotencyKey = UUID.randomUUID();
        OutboxEvent row = new OutboxEvent(
                UUID.randomUUID(),
                OutboxEventTypes.AGGREGATE_DEMO_DISTRIBUTION,
                distributionId,
                OutboxEventTypes.SEND_SHARE_EMAIL,
                shareToken.toString(),
                encryptedPayload,
                Instant.now(),
                idempotencyKey,
                1);
        outboxRepository.save(row);
        log.info("OUTBOX_EMAIL_QUEUED eventId={} distributionId={} type={}",
                row.getId(), distributionId, OutboxEventTypes.SEND_SHARE_EMAIL);
        if (outboxCipher.isEnabled()) {
            log.info("OUTBOX_PAYLOAD_ENCRYPTED eventId={} keyVersion={}",
                    row.getId(), row.getPayloadKeyVersion());
        }
        eventPublisher.publishEvent(new OutboxCreatedEvent(
                row.getId(), KafkaTopics.AUDIO_SHARE_EMAIL, OutboxEventTypes.AGGREGATE_DEMO_DISTRIBUTION));
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String extractDomain(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String sanitizeLikePattern(String raw) {
        return raw
                .replace("\\", LIKE_ESCAPE_BACKSLASH)
                .replace("%", LIKE_ESCAPE_PERCENT)
                .replace("_", LIKE_ESCAPE_UNDERSCORE);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}