package com.pwb.backend.modules.share.dto.response;

import com.pwb.backend.modules.share.entity.DemoDistribution;

import java.time.Instant;
import java.util.UUID;

public record DistributionListItemResponse(
        UUID distributionId,
        UUID threadId,
        UUID shareToken,
        String recipientEmail,
        boolean allowDownload,
        boolean revoked,
        int playCount,
        Instant lastPlayedAt,
        Instant createdAt,
        Double continuousPlayWeight,
        Long lastSessionHeartbeat,
        Long lastSessionKeysRequested,
        boolean continuousPlaySupported
) {

    public static DistributionListItemResponse from(DemoDistribution entity) {
        return new DistributionListItemResponse(
                entity.getId(),
                entity.getThreadId(),
                entity.getShareToken(),
                entity.getRecipientEmail(),
                entity.isAllowDownload(),
                entity.isRevoked(),
                entity.getPlayCount(),
                entity.getLastPlayedAt(),
                entity.getCreatedAt(),
                null, null, null, false);
    }

    public static DistributionListItemResponse fromWithContinuousPlay(DemoDistribution entity,
                                                                    double weight,
                                                                    long heartbeat,
                                                                    long keysRequested,
                                                                    boolean supported) {
        return new DistributionListItemResponse(
                entity.getId(),
                entity.getThreadId(),
                entity.getShareToken(),
                entity.getRecipientEmail(),
                entity.isAllowDownload(),
                entity.isRevoked(),
                entity.getPlayCount(),
                entity.getLastPlayedAt(),
                entity.getCreatedAt(),
                weight,
                heartbeat,
                keysRequested,
                supported);
    }
}