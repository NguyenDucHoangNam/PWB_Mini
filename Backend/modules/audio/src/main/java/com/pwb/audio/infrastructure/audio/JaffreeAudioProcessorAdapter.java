package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.domain.model.AudioProcessingRequest;
import com.pwb.audio.domain.model.AudioProcessingResult;
import com.pwb.audio.domain.service.AudioProcessorPort;
import com.pwb.audio.domain.service.StoragePort;
import com.pwb.audio.domain.service.StoredObject;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class JaffreeAudioProcessorAdapter implements AudioProcessorPort {

    private static final int DEFAULT_TIMEOUT_MINUTES = 15;
    private static final int ABORT_GRACE_SECONDS = 10;

    private final AudioProcessorProperties properties;
    private final StoragePort storagePort;
    private final AudioWorkspace workspace;
    private final AudioProbeService audioProbe;
    private final FfmpegBinaries binaries;

    @Override
    public AudioProcessingResult embedWatermark(AudioProcessingRequest request) {
        workspace.assertSpaceAvailable(estimateScratchBytes(request));

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

            FFmpegResultFuture render = binaries.ffmpeg()
                    .addInput(UrlInput.fromPath(inputFile))
                    .addInput(UrlInput.fromPath(voiceTagFile))
                    .setComplexFilter(filterComplex)
                    .addArguments("-map", WatermarkFilterBuilder.OUTPUT_LABEL)
                    .addOutput(UrlOutput.toPath(outputFile)
                            .setCodec(StreamType.AUDIO, "libmp3lame")
                            .addArguments("-b:a", properties.getOutputBitrate()))
                    .setOverwriteOutput(true)
                    .executeAsync();
            awaitRender(render, request.songId());

            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                throw new AudioBusinessException(AudioErrorCode.FFMPEG_EMPTY_OUTPUT);
            }

            long fileSize = Files.size(outputFile);
            // The mix is cut to the song (amix duration=first), so the song's duration is the output's.
            // Probing the file we just wrote would spawn a third ffprobe to re-read it for an answer
            // already in hand.
            int duration = (int) songDuration;
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
     * Waits for the render, but not forever. {@code execute()} blocks with no way out, so an FFmpeg that
     * stalls — a corrupt input it never stops reading, a filter that produces nothing — used to pin this
     * thread for the life of the process. The consumer runs one song at a time, so that one stuck song
     * stopped every song behind it, and {@code timeout-minutes} was configuration that did nothing.
     */
    private void awaitRender(FFmpegResultFuture render, UUID songId) {
        try {
            render.get(timeoutMinutes(), TimeUnit.MINUTES);
        } catch (TimeoutException ex) {
            log.error("FFmpeg exceeded its {} minute budget, stopping it: songId={}", timeoutMinutes(), songId);
            abort(render);
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_TIMED_OUT, ex);
        } catch (InterruptedException ex) {
            abort(render);
            Thread.currentThread().interrupt();
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new AudioBusinessException(AudioErrorCode.PROCESSING_FAILED, cause);
        }
    }

    /**
     * Stops the run and waits for the process to actually be gone, which on Windows is what lets the job
     * directory be deleted afterwards — a live FFmpeg still holds the output file open.
     *
     * <p>Both calls are wrapped: {@code forceStop} throws {@link java.util.concurrent.CancellationException}
     * when it wins the race against the process finishing, and the {@code get} that follows a cancelled
     * future throws it rather than {@link ExecutionException}. Neither says anything the caller can use —
     * the timeout that got us here is already the answer, and the half-written output is discarded with
     * the job directory.
     */
    private void abort(FFmpegResultFuture render) {
        try {
            render.forceStop();
        } catch (RuntimeException ex) {
            log.debug("FFmpeg stop raced the process finishing: {}", ex.getClass().getSimpleName());
        }
        try {
            render.get(ABORT_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            log.debug("FFmpeg did not exit cleanly after being stopped: {}", ex.getClass().getSimpleName());
        }
    }

    private int timeoutMinutes() {
        Integer configured = properties.getTimeoutMinutes();
        return configured != null && configured > 0 ? configured : DEFAULT_TIMEOUT_MINUTES;
    }

    private long estimateScratchBytes(AudioProcessingRequest request) {
        long sourceBytes = storagePort.findMetadata(request.inputKey())
                .map(StoredObject::sizeBytes)
                .orElse(0L);
        return (long) (sourceBytes * 2.5);
    }

    private Path download(String storageKey, Path target) {
        try (InputStream in = storagePort.download(storageKey)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.STORAGE_ERROR, ex);
        }
    }

    private double requireDuration(Path file, String what) {
        Integer duration = audioProbe.probeDuration(file);
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

}
