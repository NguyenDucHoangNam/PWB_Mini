package com.pwb.audio.domain.model;

public record TtsResult(
        byte[] audioBytes,
        Integer durationSeconds,
        String contentType
) {
}