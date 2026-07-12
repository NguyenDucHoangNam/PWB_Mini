package com.pwb.backend.modules.share.dto.response;

import java.util.UUID;

public record SharedThreadResponse(
        UUID threadId,
        UUID distributionId,
        String recipientEmail,
        String producerDisplayName,
        boolean allowDownload,
        String demoTitle,
        Double duration,
        float[] waveform,
        String playlistUrl) {
}
