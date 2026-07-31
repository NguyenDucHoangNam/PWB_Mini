package com.pwb.audio.infrastructure.audio;

import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.pwb.audio.application.exception.AudioBusinessException;
import com.pwb.audio.application.exception.AudioErrorCode;
import com.pwb.audio.infrastructure.audio.properties.AudioProcessorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.github.kokorin.jaffree.ffprobe.FFprobe")
public class AudioProbeService {

    private final AudioProcessorProperties properties;

    public Integer probeDuration(Path file) {
        try {
            FFprobeResult result = FFprobe.atPath(Paths.get(properties.getFfmpegPath()))
                    .setInput(file)
                    .execute();
            return result.getStreams().stream()
                    .filter(s -> s.getCodecType() == StreamType.AUDIO)
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
            tmp = Files.createTempFile("pwb-probe-", "-" + (suffix == null ? ".audio" : sanitize(suffix)));
            Files.write(tmp, content);
            return probeDuration(tmp);
        } catch (IOException ex) {
            throw new AudioBusinessException(AudioErrorCode.AUDIO_PROBE_FAILED, ex);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}