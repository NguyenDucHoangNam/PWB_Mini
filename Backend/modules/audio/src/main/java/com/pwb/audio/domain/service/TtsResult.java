package com.pwb.audio.domain.service;

public record TtsResult(
        byte[] audioBytes,
        Integer durationSeconds,
        String contentType
) {
}
