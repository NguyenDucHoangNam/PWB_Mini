package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrampFfmpegExecutor implements com.pwb.backend.audio.internal.helper.FfmpegExecutor {

  private final AudioProperties audioProperties;

  @Override
  public FfmpegProbeResult probe(String inputPath) {
    try {
      FFprobe ffprobe = new FFprobe(audioProperties.getWorker().getFfprobePath());
      FFmpegProbeResult result = ffprobe.probe(inputPath);
      FFmpegFormat format = result.format;
      List<FFmpegStream> streams = result.streams;
      if (streams == null || streams.isEmpty()) {
        throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED, "No streams found");
      }
      long audioCount = streams.stream()
          .filter(s -> s.codec_type == FFmpegStream.CodecType.AUDIO)
          .count();
      if (audioCount != 1) {
        throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
            "Expected exactly 1 audio stream, found " + audioCount);
      }
      boolean hasAttachment = streams.stream().anyMatch(s ->
          s.codec_type == FFmpegStream.CodecType.VIDEO
          || s.codec_type == FFmpegStream.CodecType.ATTACHMENT);
      FFmpegStream audioStream = streams.stream()
          .filter(s -> s.codec_type == FFmpegStream.CodecType.AUDIO)
          .findFirst()
          .orElseThrow();
      String codec = audioStream.codec_name;
      int sampleRate = audioStream.sample_rate > 0 ? (int) audioStream.sample_rate : 0;
      int channels = audioStream.channels > 0 ? audioStream.channels : 0;
      int bitDepth = 0;
      Integer rawBits = audioStream.bits_per_raw_sample;
      if (rawBits != null && rawBits > 0) {
        bitDepth = rawBits;
      } else if ("flac".equalsIgnoreCase(codec) || codec.toLowerCase().startsWith("pcm_")) {
        bitDepth = estimateBitDepth(codec);
      }
      double duration = format != null && format.duration > 0 ? format.duration : 0.0;
      return new FfmpegProbeResult(codec, sampleRate, bitDepth, channels, duration, hasAttachment);
    } catch (IOException ex) {
      log.error("ffprobe IO failure on {}", inputPath, ex);
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "ffprobe IO error");
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("ffprobe unexpected failure on {}", inputPath, ex);
      throw new BusinessException(ErrorCode.FFPROBE_VALIDATION_FAILED,
          "ffprobe failed");
    }
  }

  @Override
  public void watermark(String inputPath, String voiceTagPath, String outputPath, int intervalSec) {
    int delayMs = intervalSec * 1000;
    try {
      FFmpegBuilder builder = new FFmpegBuilder()
          .addInput(inputPath)
          .addInput(voiceTagPath)
          .addExtraArgs("-filter_complex",
              "[1]adelay=" + delayMs + "|" + delayMs + "[tag];"
                  + "[0][tag]sidechaincompress=threshold=0.03:ratio=12:attack=5:release=500[out]",
              "-map", "[out]");
      builder.addOutput(outputPath);
      runWithTimeout(builder);
      log.info("Watermark produced: input={}, tag={}, output={}", inputPath, voiceTagPath, outputPath);
    } catch (Exception ex) {
      log.error("FFmpeg watermark failed: input={}, tag={}", inputPath, voiceTagPath, ex);
      throw new BusinessException(ErrorCode.FFMPEG_PROCESS_FAILED,
          "Watermark failed");
    }
  }

  @Override
  public void segmentToHls(String inputPath, String outputDir, byte[] aesKeyHex, int segmentSec) {
    try {
      java.nio.file.Files.createDirectories(java.nio.file.Paths.get(outputDir));
      String keyInfoPath = outputDir + "/key.info";
      String playlistPath = outputDir + "/playlist.m3u8";
      String keyFilePath = outputDir + "/enc.key";
      String keyUri = "enc.key";
      java.nio.file.Files.write(java.nio.file.Paths.get(keyFilePath), aesKeyHex);
      String keyInfoContent = keyUri + "\n" + keyFilePath + "\n";
      java.nio.file.Files.writeString(java.nio.file.Paths.get(keyInfoPath), keyInfoContent);
      FFmpegBuilder builder = new FFmpegBuilder()
          .addInput(inputPath)
          .addExtraArgs("-f", "hls",
              "-hls_time", String.valueOf(segmentSec),
              "-hls_playlist_type", "vod",
              "-hls_segment_type", "mpegts",
              "-hls_key_info_file", keyInfoPath,
              "-hls_segment_filename", outputDir + "/seg_%03d.ts");
      builder.addOutput(playlistPath);
      runWithTimeout(builder);
      java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(keyInfoPath));
      log.info("HLS segmented: input={}, outputDir={}, playlist={}", inputPath, outputDir, playlistPath);
    } catch (Exception ex) {
      log.error("FFmpeg HLS segmentation failed: input={}, outputDir={}", inputPath, outputDir, ex);
      throw new BusinessException(ErrorCode.HLS_SEGMENTATION_FAILED,
          "HLS segmentation failed");
    }
  }

  private int estimateBitDepth(String codec) {
    return switch (codec.toLowerCase()) {
      case "pcm_s16le", "pcm_s16be" -> 16;
      case "pcm_s24le", "pcm_s24be" -> 24;
      case "pcm_s32le", "pcm_s32be" -> 32;
      case "flac" -> 16;
      default -> 0;
    };
  }

  private void runWithTimeout(FFmpegBuilder builder) throws IOException {
    FFmpeg ffmpeg = new FFmpeg(audioProperties.getWorker().getFfmpegPath());
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);
    executor.createJob(builder).run();
  }

  public static String toHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
