package com.pwb.audio.application.view;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

public record VoiceTagSuggestionView(
        UUID id,
        String name,
        String voiceName,
        String languageCode,
        VoiceTagType tagType
) {
}
