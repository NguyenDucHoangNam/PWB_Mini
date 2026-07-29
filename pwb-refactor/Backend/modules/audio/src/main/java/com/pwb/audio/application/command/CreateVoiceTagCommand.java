package com.pwb.audio.application.command;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

public record CreateVoiceTagCommand(
        UUID userId,
        String name,
        VoiceTagType tagType,
        String sourceText,
        String languageCode,
        String s3Key,
        Integer durationSeconds,
        Long fileSizeBytes
) {
}
