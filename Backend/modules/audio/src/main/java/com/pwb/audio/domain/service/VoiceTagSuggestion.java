package com.pwb.audio.domain.service;

import com.pwb.audio.domain.enums.VoiceTagType;

import java.util.UUID;

/**
 * Carries the synthesis metadata alongside the name so the picker in the song upload form can show what
 * a tag actually sounds like — which voice, which language — without a follow-up request per suggestion.
 */
public record VoiceTagSuggestion(
        UUID id,
        String name,
        String voiceName,
        String languageCode,
        VoiceTagType tagType
) {
}
