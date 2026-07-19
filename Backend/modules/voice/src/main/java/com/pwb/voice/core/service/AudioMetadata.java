package com.pwb.voice.core.service;

public record AudioMetadata(
    int durationSeconds,
    String format,
    long bitrate,
    int sampleRate
) {

    public static AudioMetadata ofDuration(int durationSeconds) {
        return new AudioMetadata(durationSeconds, null, 0L, 0);
    }
}