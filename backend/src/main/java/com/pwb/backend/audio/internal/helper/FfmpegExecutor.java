package com.pwb.backend.audio.internal.helper;

public interface FfmpegExecutor {

  FfmpegProbeResult probe(String inputPath);

  void watermark(String inputPath, String voiceTagPath, String outputPath, int intervalSec);

  void segmentToHls(String inputPath, String outputDir, byte[] aesKeyHex, int segmentSec);
}
