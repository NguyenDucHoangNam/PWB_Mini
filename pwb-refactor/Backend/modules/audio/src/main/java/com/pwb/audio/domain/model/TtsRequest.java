package com.pwb.audio.domain.model;

public record TtsRequest(
        String text,
        String languageCode,
        String voiceName
) {
}