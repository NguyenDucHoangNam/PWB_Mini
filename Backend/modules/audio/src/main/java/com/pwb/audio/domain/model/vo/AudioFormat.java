package com.pwb.audio.domain.model.vo;

import java.util.Set;

public record AudioFormat(String value) {

    private static final Set<String> SUPPORTED = Set.of("mp3", "wav", "flac");

    public AudioFormat {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audio format must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        if (!SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported audio format: " + value + ". Supported formats: " + String.join(", ", SUPPORTED));
        }
        value = normalized;
    }

    public static AudioFormat of(String value) {
        return new AudioFormat(value);
    }

    public static boolean isSupported(String value) {
        return value != null && SUPPORTED.contains(value.trim().toLowerCase());
    }
}
