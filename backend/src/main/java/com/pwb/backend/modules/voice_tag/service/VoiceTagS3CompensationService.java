package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagS3CompensationService {

    private final ObjectStorageService objectStorageService;
    private final VoiceTagProperties properties;

    @Async("voiceTagAsyncExecutor")
    public void cleanupS3Async(String s3Key, UUID ownerId, UUID tagId, int attempt, int maxAttempts) {
        VoiceTagProperties.Compensation policy = properties.getCompensation();
        if (attempt > maxAttempts || attempt > policy.getMaxAttempts()) {
            log.error("S3_COMPENSATION_ORPHAN s3Key={} ownerId={} tagId={} attempts={}",
                    s3Key, ownerId, tagId, attempt - 1);
            return;
        }

        long delayMs = computeBackoff(attempt, policy);
        if (delayMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            objectStorageService.deleteObject(StorageBucket.VOICE_TAG, s3Key);
            log.warn("VOICE_TAG_S3_DELETED_COMPENSATED s3Key={} ownerId={} tagId={} attempt={}",
                    s3Key, ownerId, tagId, attempt);
        } catch (Exception ex) {
            log.warn("VOICE_TAG_S3_CLEANUP_RETRY s3Key={} ownerId={} tagId={} attempt={} reason={}",
                    s3Key, ownerId, tagId, attempt, ex.getMessage());
            cleanupS3Async(s3Key, ownerId, tagId, attempt + 1, maxAttempts);
        }
    }

    private long computeBackoff(int attempt, VoiceTagProperties.Compensation policy) {
        if (attempt <= 1) {
            return 0L;
        }
        double multiplier = Math.max(1.0, policy.getMultiplier());
        double base = policy.getInitialDelayMillis() * Math.pow(multiplier, attempt - 2);
        long computed = (long) Math.min(base, policy.getMaxDelayMillis());
        return Math.max(0L, computed);
    }
}
