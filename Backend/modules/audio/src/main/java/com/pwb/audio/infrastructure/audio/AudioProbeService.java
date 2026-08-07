package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.github.kokorin.jaffree.ffmpeg.NullOutput;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single place audio measurements are read from. Both the watermarking pipeline and TTS need them, and
 * both would otherwise grow their own copy of the same ffprobe call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioProbeService {

    private static final int LOUDNESS_TIMEOUT_MINUTES = 5;

    /**
     * The {@code I:} line of an {@code ebur128} summary. The other LUFS lines in that summary
     * ({@code Threshold:}, {@code LRA low:}, {@code LRA high:}) must not match, hence the anchored label —
     * and the summary arrives as one multi-line message, so the anchor has to accept a newline before it.
     */
    private static final Pattern INTEGRATED_LOUDNESS =
            Pattern.compile("(?:^|\\s)I:\\s+(-?\\d+(?:\\.\\d+)?)\\s+LUFS");

    private final FfmpegBinaries binaries;

    /** Truncated to whole seconds — what the metadata columns store. */
    public Integer probeDuration(Path file) {
        return probeExactDuration(file).intValue();
    }

    /**
     * The unrounded duration. Enforcing a short-clip limit needs this: truncating first would let a 10.9s
     * file pass a "no longer than 10 seconds" check.
     */
    public Double probeExactDuration(Path file) {
        try {
            // Without setShowStreams, ffprobe reports nothing about streams and getStreams() comes back null.
            FFprobeResult result = binaries.ffprobe()
                    .setShowStreams(true)
                    .setInput(file)
                    .execute();
            List<Stream> streams = result.getStreams();
            if (streams == null) {
                throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED, "FFprobe returned no streams");
            }
            return streams.stream()
                    .filter(stream -> stream.getCodecType() == StreamType.AUDIO)
                    .map(Stream::getDuration)
                    .filter(Objects::nonNull)
                    .map(Float::doubleValue)
                    .findFirst()
                    .orElseThrow(() -> new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED,
                            "No audio stream found"));
        } catch (AudioBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("FFprobe duration extraction failed for file={}", file, ex);
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED, ex);
        }
    }

    /**
     * Integrated loudness of the whole file in LUFS, or {@code null} when FFmpeg could not put a number on
     * it. Loudness is what decides whether a voice tag is audible over a song: peak level says nothing,
     * because a mastered track sits near full scale for minutes on end while a voice recording only touches
     * it on consonants.
     *
     * <p>Failure is deliberately not an exception. Silence reads as {@code -inf}, which no number here will
     * match, and a measurement pass that dies says nothing about whether the song itself can be rendered —
     * the caller falls back to mixing at the recorded level, which is what happened before this existed.
     *
     * <p>Costs a full decode of the file, so it belongs once per song rather than inside a loop.
     */
    public Double probeLoudnessLufs(Path file) {
        AtomicReference<Double> measured = new AtomicReference<>();
        FFmpegResultFuture pass;
        try {
            pass = binaries.ffmpeg()
                    .addInput(UrlInput.fromPath(file))
                    // framelog=quiet drops the per-frame lines; the end-of-stream summary still arrives.
                    .setFilter(StreamType.AUDIO, "ebur128=framelog=quiet")
                    .addOutput(new NullOutput(false))
                    .setOutputListener(message -> {
                        Matcher matcher = INTEGRATED_LOUDNESS.matcher(message);
                        if (matcher.find()) {
                            measured.set(Double.valueOf(matcher.group(1)));
                        }
                    })
                    .executeAsync();
        } catch (RuntimeException ex) {
            log.warn("Could not start the loudness pass for {}: {}", file, ex.toString());
            return null;
        }

        try {
            pass.get(LOUDNESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            stopQuietly(pass);
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            log.warn("Could not measure the loudness of {}: {}", file, ex.toString());
            stopQuietly(pass);
            return null;
        }
        return measured.get();
    }

    public Integer probeDurationFromBytes(byte[] content, String suffix) {
        return probeExactDurationFromBytes(content, suffix).intValue();
    }

    public Double probeExactDurationFromBytes(byte[] content, String suffix) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("pwb-probe-", suffix == null ? ".audio" : sanitize(suffix));
            Files.write(tmp, content);
            return probeExactDuration(tmp);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED, ex);
        } finally {
            deleteQuietly(tmp);
        }
    }

    /**
     * Stops a measurement pass that will not be waited on again. {@code forceStop} throws
     * {@link java.util.concurrent.CancellationException} when it races the process finishing on its own,
     * which says nothing worth acting on here — the measurement has already been given up.
     */
    private void stopQuietly(FFmpegResultFuture pass) {
        try {
            pass.forceStop();
        } catch (RuntimeException ex) {
            log.debug("Loudness pass stop raced the process finishing: {}", ex.getClass().getSimpleName());
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete probe temp file {}: {}", file, ex.getMessage());
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
