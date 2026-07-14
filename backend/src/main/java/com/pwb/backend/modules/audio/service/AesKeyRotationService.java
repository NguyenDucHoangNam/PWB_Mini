package com.pwb.backend.modules.audio.service;

import org.springframework.data.domain.PageRequest;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.service.crypto.AesKeyEncryptor;
import com.pwb.backend.modules.share.constant.ShareRedisKeys;
import com.pwb.backend.modules.share.entity.DemoDistribution;
import com.pwb.backend.modules.share.repository.DemoDistributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AesKeyRotationService {

    private static final Duration COOKIE_JTI_TTL = Duration.ofSeconds(1800L);

    private final DemoRepository demoRepository;
    private final DemoDistributionRepository demoDistributionRepository;
    private final AesKeyEncryptor aesKeyEncryptor;
    private final StreamKeyCacheService streamKeyCacheService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.audio.hls.key-rotation-enabled}")
    private boolean keyRotationEnabled;

    @Transactional
    public RotationResult rotateOnDemand(UUID demoId, UUID producerId) {
        ensureEnabled();
        Demo demo = assertOwnedAndActive(demoId, producerId);
        return persistNewKey(demo, "manual");
    }

    @Transactional
    public RotationResult rotateOnRevoke(UUID demoId, UUID sourceShareToken, String reason) {
        ensureEnabled();
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                        "Demo " + demoId + " not found for rotation-on-revoke"));
        if (demo.getStatus() != DemoStatus.ACTIVE) {
            log.info("AES_ROTATE_SKIPPED_NOT_ACTIVE demoId={} status={}", demoId, demo.getStatus());
            return new RotationResult(demoId, demo.getAesKeyVersion(), false);
        }
        RotationResult result = persistNewKey(demo, "on-revoke:" + reason);
        revokeJtisForShareToken(sourceShareToken);
        return result;
    }

    private RotationResult persistNewKey(Demo demo, String trigger) {
        int oldVersion = demo.getAesKeyVersion();
        byte[] newRaw = aesKeyEncryptor.generateAes128Key();
        byte[] newEncrypted = aesKeyEncryptor.encrypt(newRaw);
        Instant now = Instant.now();
        demo.rotateKey(newEncrypted, now);
        demoRepository.save(demo);
        streamKeyCacheService.evict(demo.getId());
        log.warn("AES_KEY_ROTATED demoId={} oldVersion={} newVersion={} trigger={}",
                demo.getId(), oldVersion, demo.getAesKeyVersion(), trigger);
        return new RotationResult(demo.getId(), demo.getAesKeyVersion(), true);
    }

    private Demo assertOwnedAndActive(UUID demoId, UUID producerId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                        "Demo " + demoId + " not found for rotate-key"));
        if (demo.getOwnerId() == null || !demo.getOwnerId().equals(producerId)) {
            log.warn("AES_ROTATE_FORBIDDEN demoId={} producerId={}", demoId, producerId);
            throw new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                    "Demo not accessible to producer");
        }
        if (demo.getStatus() != DemoStatus.ACTIVE) {
            log.warn("AES_ROTATE_DEMO_NOT_ACTIVE demoId={} status={}", demoId, demo.getStatus());
            throw new BusinessException(AudioErrorCode.DEMO_NOT_ACTIVE);
        }
        return demo;
    }

    public void revokeJtisForDistribution(UUID shareToken) {
        revokeJtisForShareToken(shareToken);
    }

    private void revokeJtisForShareToken(UUID shareToken) {
        if (shareToken == null) {
            return;
        }
        try {
            String activeKey = ShareRedisKeys.activeCookieSessionSetKey(shareToken);
            Set<String> jtis = stringRedisTemplate.opsForSet().members(activeKey);
            if (jtis == null || jtis.isEmpty()) {
                return;
            }
            for (String jti : jtis) {
                String revokedKey = ShareRedisKeys.cookieRevokedKey(jti);
                stringRedisTemplate.opsForValue().set(revokedKey, "1", COOKIE_JTI_TTL);
            }
            stringRedisTemplate.delete(activeKey);
            log.warn("COOKIE_JTI_BULK_REVOKED shareToken={} count={}", shareToken, jtis.size());
        } catch (Exception ex) {
            log.warn("COOKIE_JTI_BULK_REVOKE_FAILED shareToken={} reason={}",
                    shareToken, ex.getMessage());
        }
    }

    public void markKeyCompromised(UUID demoId) {
        Demo demo = demoRepository.findById(demoId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                        "Demo " + demoId + " not found for AES_KEY_COMPROMISED"));
        log.error("AES_KEY_COMPROMISED demoId={} keyVersion={}", demoId, demo.getAesKeyVersion());
        streamKeyCacheService.evict(demoId);
        for (DemoDistribution distribution : demoDistributionRepository.findByDemoIdAndRevokedFalse(
                demoId, PageRequest.of(0, 1000)).getContent()) {
            revokeJtisForDistribution(distribution.getShareToken());
        }
    }

    private void ensureEnabled() {
        if (!keyRotationEnabled) {
            throw new BusinessException(AudioErrorCode.DEMO_NOT_ACTIVE,
                    "AES key rotation is disabled by configuration");
        }
    }

    public record RotationResult(UUID demoId, int newVersion, boolean rotated) {}
}
