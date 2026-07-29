package com.pwb.audio.application.command;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

public record UpdateVoiceTagCommand(
        UUID userId,
        UUID voiceTagId,
        String name,
        String sourceText,
        String languageCode
) {
}
