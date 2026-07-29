package com.pwb.audio.domain.model;

import java.util.UUID;

public record AudioProcessingRequest(
        UUID songId,
        String inputKey,
        String voiceTagKey,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer fadeInMs,
        Integer fadeOutMs,
        Integer startOffsetSeconds,
        String outputKey
) {
}