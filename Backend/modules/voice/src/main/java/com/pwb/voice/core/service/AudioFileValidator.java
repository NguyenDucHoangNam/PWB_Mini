package com.pwb.voice.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;

import com.pwb.storage.infrastructure.util.MediaTypeUtils;
import com.pwb.voice.infrastructure.audio.AudioMetadataExtractor;
import com.pwb.voice.infrastructure.config.AudioProcessingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioFileValidator {

    private static final int MAGIC_BYTE_HEAD_SIZE = 12;
    private static final String COMMA_DELIMITER = ",";
    private static final String AUDIO_MPEG = "audio/mpeg";
    private static final String AUDIO_WAV = "audio/wav";
    private static final String AUDIO_FLAC = "audio/flac";

    private final AudioProcessingProperties audioProcessingProperties;
    private final AudioMetadataExtractor audioMetadataExtractor;

    private Set<String> allowedExtensionsLowerCase;
    private Map<String, String> extensionToExpectedMime;

    @PostConstruct
    void init() {
        this.allowedExtensionsLowerCase = new HashSet<>();
        for (String raw : audioProcessingProperties.getAllowedFormats().split(COMMA_DELIMITER)) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                allowedExtensionsLowerCase.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        Map<String, String> map = new HashMap<>();
        map.put("mp3", AUDIO_MPEG);
        map.put("wav", AUDIO_WAV);
        map.put("flac", AUDIO_FLAC);
        this.extensionToExpectedMime = map;

        log.info("AudioFileValidator initialized: allowedFormats={}",
                allowedExtensionsLowerCase);
    }

    public void validateExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException(VoiceErrorCode.INVALID_AUDIO_FORMAT);
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (!allowedExtensionsLowerCase.contains(normalized)) {
            throw new BusinessException(VoiceErrorCode.INVALID_AUDIO_FORMAT);
        }
    }

    public void validateMagicBytes(InputStream audioStream, String extension) {
        byte[] head = readHead(audioStream, MAGIC_BYTE_HEAD_SIZE);
        String detectedMime = MediaTypeUtils.detectFromBytes(head);
        String expectedMime = extensionToExpectedMime.get(extension.toLowerCase(Locale.ROOT));

        if (expectedMime == null || !expectedMime.equals(detectedMime)) {
            throw new BusinessException(VoiceErrorCode.INVALID_AUDIO_FORMAT);
        }
    }

    public void validateSize(long sizeBytes) {
        if (sizeBytes <= 0L) {
            throw new BusinessException(VoiceErrorCode.FILE_TOO_LARGE);
        }
        if (sizeBytes > audioProcessingProperties.getMaxFileSizeBytes()) {
            throw new BusinessException(VoiceErrorCode.FILE_TOO_LARGE);
        }
    }

    public AudioMetadata validateDuration(InputStream audioStream, String extension) {
        try {
            AudioMetadata metadata = audioMetadataExtractor.extract(audioStream, extension);
            if (metadata.durationSeconds() > audioProcessingProperties.getMaxDurationSeconds()) {
                throw new BusinessException(VoiceErrorCode.INVALID_AUDIO_DURATION);
            }
            return metadata;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Audio probing failed: {}", ex.getMessage(), ex);
            throw new BusinessException(VoiceErrorCode.AUDIO_PROCESSING_FAILED);
        }
    }

    private byte[] readHead(InputStream stream, int size) {
        byte[] buffer = new byte[size];
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream(size)) {
            int total = 0;
            while (total < size) {
                int read = in.read(buffer, total, size - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total > 0) {
                out.write(buffer, 0, total);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(VoiceErrorCode.AUDIO_PROCESSING_FAILED);
        }
    }
}

