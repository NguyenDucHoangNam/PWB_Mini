package com.pwb.backend.audio.internal.application.helper;

public record FfmpegProbeResult(
    String codecName,
    int sampleRate,
    int bitDepth,
    int channels,
    double durationSeconds,
    boolean hasCoverArt
) {}
