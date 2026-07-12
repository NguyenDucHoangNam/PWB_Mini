package com.pwb.backend.modules.audio.service.ffmpeg;

import java.math.BigDecimal;
import java.util.Set;

public record AudioAnalysisResult(
        String codecName,
        BigDecimal durationSeconds,
        int sampleRate,
        int channels,
        int bitDepth,
        boolean hasAttachedPicture,
        long fileSizeBytes) {

    public static final Set<String> ALLOWED_CODECS = Set.of(
            "pcm_s16le", "pcm_s24le", "pcm_s32le", "flac", "mp3");

    public static final Set<Integer> ALLOWED_SAMPLE_RATES = Set.of(
            22050, 44100, 48000, 88200, 96000);

    public static final BigDecimal MIN_DURATION = new BigDecimal("1.0");
    public static final BigDecimal MAX_DURATION = new BigDecimal("1800.0");

    public boolean isCodecAllowed() {
        return codecName != null && ALLOWED_CODECS.contains(codecName);
    }

    public boolean isSampleRateAllowed() {
        return ALLOWED_SAMPLE_RATES.contains(sampleRate);
    }

    public boolean isBitDepthAllowed() {
        return "flac".equals(codecName) || "mp3".equals(codecName) || bitDepth >= 16;
    }

    public boolean isDurationAllowed() {
        return durationSeconds != null
                && durationSeconds.compareTo(MIN_DURATION) >= 0
                && durationSeconds.compareTo(MAX_DURATION) <= 0;
    }
}