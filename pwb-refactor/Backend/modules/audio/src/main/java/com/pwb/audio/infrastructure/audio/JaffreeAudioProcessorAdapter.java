package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;
import com.pwb.audio.domain.service.AudioProcessorPort;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import com.pwb.audio.infrastructure.service.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnClass(name = "com.github.kokorin.jaffree.ffmpeg.FFmpeg")
@ConditionalOnProperty(name = "pwb.audio.processor.mode", havingValue = "local", matchIfMissing = true)
public class JaffreeAudioProcessorAdapter implements AudioProcessorPort {

    private static final String VOICE_LABEL = "[tag]";
    private static final String OUTPUT_LABEL = "[out]";

    private final AudioProcessorProperties properties;
    private final StoragePort storagePort;

    public JaffreeAudioProcessorAdapter(AudioProcessorProperties properties, StoragePort storagePort) {
        this.properties = properties;
        this.storagePort = storagePort;
    }

    @Override
    public AudioProcessingResult embedWatermark(AudioProcessingRequest request) {
        Path inputFile = null;
        Path voiceTagFile = null;
        Path outputFile = null;

        try {
            Files.createDirectories(Paths.get(properties.getWorkingDir()));

            inputFile = downloadToTemp(request.songId() + "-input", request.inputKey());
            voiceTagFile = downloadToTemp(request.songId() + "-voice", request.voiceTagKey());
            outputFile = Paths.get(properties.getWorkingDir(), request.songId() + "-output.mp3");

            String filterComplex = buildFilterComplex(
                    request.volumePercentage(),
                    request.fadeInMs(),
                    request.fadeOutMs(),
                    request.startOffsetSeconds());

            FFmpegResult result = FFmpeg.atPath(Paths.get(properties.getFfmpegPath()))
                    .addInput(UrlInput.fromPath(inputFile))
                    .addInput(UrlInput.fromPath(voiceTagFile))
                    .setComplexFilter(filterComplex)
                    .addOutput(UrlOutput.toPath(outputFile)
                            .setDuration(request.intervalSeconds(), TimeUnit.SECONDS))
                    .execute();

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                throw new AudioBusinessException(AudioErrorCode.FFMPEG_EMPTY_OUTPUT);
            }

            long fileSize = Files.size(outputFile);
            Integer duration = probeDuration(outputFile);
            storagePort.uploadFromPath(request.outputKey(), outputFile, fileSize);

            log.info("Watermark embedded: songId={}, outputKey={}, size={}, duration={}",
                    request.songId(), request.outputKey(), fileSize, duration);

            return new AudioProcessingResult(request.songId(), request.outputKey(), duration, fileSize);
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.error("Watermark processing failed: songId={}", request.songId(), ex);
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        } finally {
            cleanup(inputFile, voiceTagFile, outputFile);
        }
    }

    private Path downloadToTemp(String prefix, String s3Key) {
        Path target = Paths.get(properties.getWorkingDir(), prefix + "-" + System.nanoTime() + ".tmp");
        try (var in = storagePort.download(s3Key)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            AudioBusinessException ex2 = new AudioBusinessException(
                    AudioErrorCode.STORAGE_ERROR,
                    "Failed to download object: " + s3Key);
            ex2.initCause(ex);
            throw ex2;
        }
    }

    private String buildFilterComplex(Integer volume, Integer fadeInMs, Integer fadeOutMs, Integer startOffset) {
        int vol = Optional.ofNullable(volume).orElse(properties.getDefaultVolumePercentage());
        int fadeIn = Optional.ofNullable(fadeInMs).orElse(1000);
        int fadeOut = Optional.ofNullable(fadeOutMs).orElse(1000);
        int start = Optional.ofNullable(startOffset).orElse(0);

        double volumeFactor = Math.max(0.0, Math.min(vol, 100)) / 100.0;
        double fadeInSeconds = Math.max(0.0, fadeIn / 1000.0);
        double fadeOutSeconds = Math.max(0.0, fadeOut / 1000.0);

        String voiceChain = "[1:a]volume=" + String.format("%.2f", volumeFactor) +
                ",afade=t=in:st=0:d=" + String.format("%.2f", fadeInSeconds) +
                ",afade=t=out:st=" + (double) start + ":d=" + String.format("%.2f", fadeOutSeconds) +
                "[" + VOICE_LABEL + "]";

        String mixChain = ";[0:a][" + VOICE_LABEL + "]amix=inputs=2:duration=first:dropout_transition=0[" + OUTPUT_LABEL
                + "]";

        return voiceChain + mixChain;
    }

    private Integer probeDuration(Path file) {
        try {
            FFprobeResult probe = FFprobe.atPath(Paths.get(properties.getFfmpegPath()))
                    .setInput(file)
                    .execute();
            return probe.getStreams().stream()
                    .filter(s -> s.getCodecType() == StreamType.AUDIO)
                    .map(Stream::getDuration)
                    .filter(Objects::nonNull)
                    .map(Float::intValue)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("FFprobe duration extraction failed", ex);
            return null;
        }
    }

    private void cleanup(Path... files) {
        if (!Boolean.TRUE.equals(properties.getCleanupOnSuccess())) {
            return;
        }
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ex) {
                    log.debug("Temp file cleanup failed: {}", file, ex);
                }
            }
        }
    }
}