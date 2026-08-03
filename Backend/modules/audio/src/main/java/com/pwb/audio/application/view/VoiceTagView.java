package com.pwb.audio.application.view;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.time.Instant;
import java.util.UUID;

public record VoiceTagView(
        UUID id,
        UUID userId,
        String name,
        VoiceTagType tagType,
        String sourceText,
        String languageCode,
        String s3Key,
        Integer durationSeconds,
        Long fileSizeBytes,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
