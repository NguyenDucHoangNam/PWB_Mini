package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfigureVoiceTagRequest(
        @NotNull UUID songId,
        @NotNull UUID voiceTagId,
        @NotNull @Min(5) @Max(3600) Integer intervalSeconds,
        @NotNull @Min(0) @Max(100) Integer volumePercentage,
        @NotNull @Min(0) Integer fadeInDurationMs,
        @NotNull @Min(0) Integer fadeOutDurationMs,
        @NotNull @Min(0) Integer startOffsetSeconds,
        boolean enabled
) {
}
