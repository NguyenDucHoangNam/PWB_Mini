package com.pwb.backend.modules.voice_tag.dto.response;

import com.pwb.backend.modules.voice_tag.entity.VoiceTag;

import java.time.Instant;
import java.util.UUID;

public record VoiceTagResponse(
        UUID id,
        String textContent,
        String languageCode,
        String voiceName,
        boolean isDefault,
        Instant createdAt) {

    public static VoiceTagResponse fromEntity(VoiceTag tag) {
        return new VoiceTagResponse(
                tag.getId(),
                tag.getTextContent(),
                tag.getLanguageCode(),
                tag.getVoiceName(),
                tag.isDefault(),
                tag.getCreatedAt());
    }
}
