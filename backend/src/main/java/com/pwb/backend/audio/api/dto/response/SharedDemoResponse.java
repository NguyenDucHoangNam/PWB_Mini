package com.pwb.backend.audio.api.dto.response;

import java.time.Instant;
import java.util.List;

public record SharedDemoResponse(
    String threadId,
    String recipientEmail,
    String producerDisplayName,
    boolean allowDownload,
    String demoTitle,
    Double duration,
    List<Float> waveform,
    String playlistUrl,
    Instant expiresAt
) {}