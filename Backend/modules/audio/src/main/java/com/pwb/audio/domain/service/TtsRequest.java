package com.pwb.audio.domain.service;

public record TtsRequest(
        String text,
        String languageCode,
        String voiceName
) {
}
