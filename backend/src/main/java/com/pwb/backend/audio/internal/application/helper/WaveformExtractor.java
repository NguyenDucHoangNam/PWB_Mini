package com.pwb.backend.audio.internal.application.helper;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaveformExtractor {

  private final com.pwb.backend.audio.internal.application.helper.FfmpegExecutor ffmpegExecutor;
  private final com.pwb.backend.audio.internal.interfaces.config.AudioProperties audioProperties;

  public List<Float> extract(String inputPath, int points) {
    ProcessBuilder pb = new ProcessBuilder(
        audioProperties.getWorker().getFfmpegPath(),
        "-hide_banner",
        "-loglevel", "error",
        "-nostats",
        "-i", inputPath,
        "-ac", "1",
        "-ar", "8000",
        "-map", "0:a",
        "-c:a", "pcm_s16le",
        "-f", "data",
        "-"
    );
    pb.redirectErrorStream(false);
    Process process = null;
    try {
      process = pb.start();
      Thread stderrDrain = drainStdErr(process);
      byte[] rawAudio = process.getInputStream().readAllBytes();
      int exit = process.waitFor();
      stderrDrain.join(5000);
      if (exit != 0) {
        throw new BusinessException(ErrorCode.FFMPEG_PROCESS_FAILED,
            "Waveform extraction failed with code " + exit);
      }
      return bucketRms(rawAudio, points);
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("Waveform extraction failed", ex);
      throw new BusinessException(ErrorCode.FFMPEG_PROCESS_FAILED,
          "Waveform extraction failed");
    } finally {
      destroyQuietly(process);
    }
  }

  private Thread drainStdErr(Process process) {
    Thread t = new Thread(() -> {
      try (java.io.InputStream err = process.getErrorStream();
           java.io.BufferedReader reader = new java.io.BufferedReader(
               new java.io.InputStreamReader(err, java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          log.warn("ffmpeg waveform stderr: {}", line);
        }
      } catch (IOException ignored) {
      }
    }, "waveform-stderr-drain");
    t.setDaemon(true);
    t.start();
    return t;
  }

  private void destroyQuietly(Process process) {
    if (process == null || !process.isAlive()) {
      return;
    }
    process.destroy();
    try {
      if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private List<Float> bucketRms(byte[] rawAudio16le, int points) {
    List<Float> result = new ArrayList<>(points);
    if (rawAudio16le == null || rawAudio16le.length < 2) {
      for (int i = 0; i < points; i++) {
        result.add(0f);
      }
      return result;
    }
    int bytesPerSample = 2;
    int totalSamples = rawAudio16le.length / bytesPerSample;
    int bucketSize = Math.max(1, totalSamples / points);
    double globalMax = 1.0;
    for (int b = 0; b < points; b++) {
      int start = b * bucketSize;
      int end = Math.min(start + bucketSize, totalSamples);
      if (start >= end) {
        result.add(0f);
        continue;
      }
      double sumSquares = 0;
      int count = 0;
      for (int i = start; i < end; i++) {
        int lo = rawAudio16le[i * 2] & 0xff;
        int hi = rawAudio16le[i * 2 + 1];
        int sample = (hi << 8) | lo;
        sumSquares += (double) sample * sample;
        count++;
      }
      double rms = count > 0 ? Math.sqrt(sumSquares / count) : 0;
      if (rms > globalMax) {
        globalMax = rms;
      }
      double normalized = rms / 32768.0;
      result.add((float) Math.min(1.0, normalized));
    }
    return result;
  }

  private String readStream(java.io.InputStream is) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    }
  }
}
