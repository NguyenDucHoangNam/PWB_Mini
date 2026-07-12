package com.pwb.backend.modules.audio.dto.response;

import com.pwb.backend.modules.audio.enums.DemoStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DemoStatusResponse(
        UUID demoId,
        DemoStatus status,
        String title,
        BigDecimal duration,
        Integer sampleRate,
        String format,
        List<Double> waveform,
        String hlsPlaylistUrl,
        String errorMessage) {
}