package com.pwb.audio.domain.model;

import java.util.UUID;

public record AudioProcessingResult(
        UUID songId,
        String outputKey,
        Integer durationSeconds,
        Long fileSizeBytes
) {
}