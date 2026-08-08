package com.pwb.audio.application.view;

public record TtsPreview(
        byte[] audioBytes,
        String contentType,
        Integer durationSeconds
) {
}
