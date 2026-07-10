package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;

import java.util.Set;

public final class FfmpegValidator {

  private FfmpegValidator() {}

  private static final Set<String> ALLOWED_CODECS = Set.of(
      "pcm_s16le", "pcm_s24le", "pcm_s32le", "flac", "mp3"
  );

  private static final Set<Integer> ALLOWED_SAMPLE_RATES = Set.of(
      22050, 44100, 48000, 88200, 96000
  );

  private static final double MIN_DURATION_SECONDS = 1.0;
  private static final double MAX_DURATION_SECONDS = 1800.0;

  public static void validate(FfmpegProbeResult probe) {
    if (probe == null) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "ffprobe returned no information");
    }
    String codec = probe.codecName();
    if (codec == null || !ALLOWED_CODECS.contains(codec.toLowerCase())) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "Unsupported codec: " + codec + ". Allowed: " + ALLOWED_CODECS);
    }
    if (!ALLOWED_SAMPLE_RATES.contains(probe.sampleRate())) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "Invalid sample rate: " + probe.sampleRate() + ". Allowed: " + ALLOWED_SAMPLE_RATES);
    }
    if (codec.startsWith("pcm_") && probe.bitDepth() < 16) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "PCM bit depth must be >= 16, got: " + probe.bitDepth());
    }
    if (probe.channels() != 1) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "Audio must be 1 channel (mono), got: " + probe.channels());
    }
    if (probe.durationSeconds() < MIN_DURATION_SECONDS || probe.durationSeconds() > MAX_DURATION_SECONDS) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "Duration must be between 1s and 1800s, got: " + probe.durationSeconds());
    }
    if (probe.hasCoverArt()) {
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "Cover art/attached image is not allowed");
    }
  }
}
