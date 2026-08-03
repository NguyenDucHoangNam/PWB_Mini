package com.pwb.audio.domain.model.vo;

public record AudioFormat(String value) {

    private static final String MP3 = "mp3";
    private static final String WAV = "wav";
    private static final String FLAC = "flac";

    public AudioFormat {
        String normalized = value.trim().toLowerCase();
        if (!isSupported(normalized)) {
            throw new IllegalArgumentException("Unsupported audio format: " + value + ". Supported formats: mp3, wav, flac");
        }
        value = normalized;
    }

    public static AudioFormat of(String value) {
        return new AudioFormat(value);
    }

    public static boolean isSupported(String value) {
        String normalized = value.trim().toLowerCase();
        return MP3.equals(normalized) || WAV.equals(normalized) || FLAC.equals(normalized);
    }

    public boolean isMp3() {
        return MP3.equals(value);
    }

    public boolean isWav() {
        return WAV.equals(value);
    }

    public boolean isFlac() {
        return FLAC.equals(value);
    }
}
