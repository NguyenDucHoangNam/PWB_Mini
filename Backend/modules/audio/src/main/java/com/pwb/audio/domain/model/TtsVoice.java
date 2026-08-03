package com.pwb.audio.domain.model;

public record TtsVoice(
        String name,
        String languageCode,
        String gender
) {
}