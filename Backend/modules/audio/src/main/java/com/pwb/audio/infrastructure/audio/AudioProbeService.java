package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
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

/**
 * The single place audio durations are read from. Both the watermarking pipeline and TTS need them, and
 * both would otherwise grow their own copy of the same ffprobe call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioProbeService {

    private final FfmpegBinaries binaries;

    public Integer probeDuration(Path file) {
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
                    .map(Float::intValue)
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

    public Integer probeDurationFromBytes(byte[] content, String suffix) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("pwb-probe-", suffix == null ? ".audio" : sanitize(suffix));
            Files.write(tmp, content);
            return probeDuration(tmp);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED, ex);
        } finally {
            deleteQuietly(tmp);
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
