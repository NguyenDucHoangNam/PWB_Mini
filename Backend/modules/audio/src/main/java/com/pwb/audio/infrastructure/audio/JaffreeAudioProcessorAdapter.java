package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
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
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.github.kokorin.jaffree.ffmpeg.FFmpeg")
@ConditionalOnProperty(name = "pwb.audio.processor.mode", havingValue = "local", matchIfMissing = true)
public class JaffreeAudioProcessorAdapter implements AudioProcessorPort {

    private final AudioProcessorProperties properties;
    private final StoragePort storagePort;
    private final AudioWorkspace workspace;

    @Override
    public AudioProcessingResult embedWatermark(AudioProcessingRequest request) {
        Path jobDir = workspace.createJobDirectory(request.songId());
        try {
            Path inputFile = download(request.inputKey(), jobDir.resolve("input"));
            Path voiceTagFile = download(request.voiceTagKey(), jobDir.resolve("voice-tag"));
            Path outputFile = jobDir.resolve("output.mp3");

            double songDuration = requireDuration(inputFile, "song");
            double voiceTagDuration = requireDuration(voiceTagFile, "voice tag");
            assertIntervalFitsTag(request, voiceTagDuration);

            String filterComplex = WatermarkFilterBuilder.build(request, songDuration, voiceTagDuration);
            log.debug("FFmpeg filter for songId={}: {}", request.songId(), filterComplex);

            FFmpeg.atPath(Paths.get(properties.getFfmpegPath()))
                    .addInput(UrlInput.fromPath(inputFile))
                    .addInput(UrlInput.fromPath(voiceTagFile))
                    .setComplexFilter(filterComplex)
                    .addArguments("-map", WatermarkFilterBuilder.OUTPUT_LABEL)
                    .addOutput(UrlOutput.toPath(outputFile))
                    .setOverwriteOutput(true)
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
            workspace.release(jobDir);
        }
    }

    /**
     * Writes straight into the job directory, so a copy that dies midway leaves its remains where the
     * {@code finally} above will still sweep them.
     */
    private Path download(String storageKey, Path target) {
        try (InputStream in = storagePort.download(storageKey)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    /**
     * Both durations drive the filtergraph — how many insertions fit and how long each duck lasts — so an
     * unreadable file has to stop the job rather than silently produce a wrong mix.
     */
    private double requireDuration(Path file, String what) {
        Integer duration = probeDuration(file);
        if (duration == null || duration <= 0) {
            log.error("Could not read {} duration from {}", what, file);
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED);
        }
        return duration;
    }

    private void assertIntervalFitsTag(AudioProcessingRequest request, double voiceTagDuration) {
        if (request.intervalSeconds() <= voiceTagDuration) {
            log.warn("Interval {}s is shorter than the {}s voice tag: songId={}",
                    request.intervalSeconds(), voiceTagDuration, request.songId());
            throw new AudioBusinessException(AudioErrorCode.INVALID_TAG_INTERVAL);
        }
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
            log.warn("FFprobe duration extraction failed for {}: {}", file, ex.getMessage());
            return null;
        }
    }
}
