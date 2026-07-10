package com.pwb.backend.audio.internal.api;

import java.math.BigDecimal;
import java.util.List;

public record DemoStatusResponse(
    String demoId,
    String status,
    String errorMessage,
    List<Float> waveformData,
    BigDecimal duration,
    Integer sampleRate,
    String format,
    String hlsPlaylistS3Key
) {}
