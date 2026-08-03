package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param intervalSeconds     gap between the start of one tag and the start of the next
 * @param startOffsetSeconds  when the first tag is placed
 * @param volumePercentage    how loud the voice tag itself plays
 * @param duckingPercentage   volume the song keeps while a tag plays; 100 leaves the song untouched
 */
public record ConfigureVoiceTagRequest(
        @NotNull UUID voiceTagId,
        @NotNull @Min(5) @Max(600) Integer intervalSeconds,
        @NotNull @Min(0) @Max(100) Integer volumePercentage,
        @NotNull @Min(0) @Max(100) Integer duckingPercentage,
        @NotNull @Min(0) Integer startOffsetSeconds,
        boolean enabled
) {
}
