package com.pwb.backend.modules.share.dto.response;

import java.util.UUID;

public record DistributeDemoResponse(
        UUID distributionId,
        UUID threadId,
        UUID shareToken,
        String recipientEmail,
        boolean allowDownload,
        String shareLink
) {
}