package com.pwb.voice.infrastructure.audio;

import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Format;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.pwb.voice.core.service.AudioMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Component
public class AudioMetadataExtractor {

    private static final String TEMP_FILE_PREFIX = "voice-probe-";

    public AudioMetadata extract(InputStream audioStream, String extension) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(TEMP_FILE_PREFIX, "." + safeSuffix(extension));
            Files.copy(audioStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return probe(tempFile);
        } catch (IOException ex) {
            log.error("Audio probe failed: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to stage audio for probing", ex);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private AudioMetadata probe(Path tempFile) {
        FFprobeResult result = FFprobe.atPath()
                .setShowFormat(true)
                .setShowStreams(true)
                .setInput(tempFile)
                .execute();

        Format format = result.getFormat();
        if (format == null) {
            throw new IllegalStateException("FFprobe returned no format data");
        }

        Double durationSeconds = format.getDuration() == null ? null : format.getDuration().doubleValue();
        if (durationSeconds == null) {
            List<Stream> streams = result.getStreams();
            if (streams != null) {
                for (Stream stream : streams) {
                    if (stream.getDuration() != null) {
                        durationSeconds = stream.getDuration().doubleValue();
                        break;
                    }
                }
            }
        }

        if (durationSeconds == null) {
            throw new IllegalStateException("FFprobe returned no duration");
        }

        int durationInt = (int) Math.round(durationSeconds);
        long bitRate = format.getBitRate() == null ? 0L : format.getBitRate();
        int sampleRate = 0;

        List<Stream> streams = result.getStreams();
        if (streams != null) {
            for (Stream stream : streams) {
                if (stream.getSampleRate() != null) {
                    sampleRate = stream.getSampleRate();
                    break;
                }
            }
        }

        return new AudioMetadata(durationInt, format.getFormatName(), bitRate, sampleRate);
    }

    private String safeSuffix(String extension) {
        if (extension == null || extension.isBlank()) {
            return "bin";
        }
        String cleaned = extension.toLowerCase().trim();
        if (!cleaned.matches("[a-z0-9]+")) {
            return "bin";
        }
        return cleaned;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete probe temp file: path={}", path, ex);
        }
    }
}