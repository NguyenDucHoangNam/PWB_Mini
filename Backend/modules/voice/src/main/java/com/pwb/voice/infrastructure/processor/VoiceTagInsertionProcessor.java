package com.pwb.voice.infrastructure.processor;

import com.pwb.backend.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;

import com.pwb.outbox.infrastructure.messaging.OutboxKafkaConfig;
import com.pwb.storage.api.StorageService;
import com.pwb.voice.api.event.VoiceProcessingRequestedIntegrationEvent;
import com.pwb.voice.core.model.SongTagConfig;
import com.pwb.voice.core.service.AudioProcessingService;
import com.pwb.voice.infrastructure.config.AudioProcessingProperties;
import com.pwb.voice.infrastructure.config.VoiceProperties;
import com.pwb.voice.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.voice.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.voice.infrastructure.persistence.mapper.SongTagConfigMapper;
import com.pwb.voice.infrastructure.persistence.repository.SongJpaRepository;
import com.pwb.voice.infrastructure.persistence.repository.SongTagConfigJpaRepository;
import com.pwb.voice.infrastructure.persistence.repository.VoiceTagJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagInsertionProcessor {

    private static final String DOT = ".";
    private static final String KEY_DELIMITER = "/";

    private final SongJpaRepository songRepository;
    private final SongTagConfigJpaRepository configRepository;
    private final VoiceTagJpaRepository voiceTagRepository;
    private final StorageService storageService;
    private final AudioProcessingService audioProcessingService;
    private final VoiceProcessingPersistenceService persistenceService;
    private final SongTagConfigMapper songTagConfigMapper;
    private final VoiceProperties voiceProperties;
    private final AudioProcessingProperties audioProcessingProperties;

    @KafkaListener(
        topics = OutboxKafkaConfig.TOPIC_VOICE_PROCESSING,
        groupId = "voice-processor",
        containerFactory = "voiceKafkaListenerContainerFactory"
    )
    public void consume(VoiceProcessingRequestedIntegrationEvent event) {
        UUID songId = event.songId();
        UUID userId = event.userId();
        Path originalPath = null;
        Path tagPath = null;
        Path outputPath = null;

        try {
            SongJpaEntity songEntity = songRepository
                    .findByIdAndUserIdAndDeletedFalse(songId, userId)
                    .orElseThrow(() -> new BusinessException(VoiceErrorCode.SONG_NOT_FOUND));

            SongTagConfigJpaEntity configEntity = configRepository
                    .findBySongIdAndDeletedFalse(songId)
                    .orElse(null);

            if (configEntity == null || !configEntity.isEnabled()) {
                log.info("Skip voice processing (no active config): songId={}, hasConfig={}, enabled={}",
                        songId, configEntity != null, configEntity != null && configEntity.isEnabled());
                persistenceService.markSkippedNoConfig(songId);
                return;
            }

            VoiceTagJpaEntity tagEntity = voiceTagRepository
                    .findByIdAndUserIdAndDeletedFalse(configEntity.getVoiceTagId(), userId)
                    .orElseThrow(() -> new BusinessException(VoiceErrorCode.VOICE_TAG_NOT_FOUND));

            Files.createDirectories(Path.of(audioProcessingProperties.getTempDir()));
            String extension = songEntity.getFormat() == null ? "mp3" : songEntity.getFormat();
            originalPath = Files.createTempFile(Path.of(audioProcessingProperties.getTempDir()),
                    "voice-original-" + songId + "-", DOT + extension);
            tagPath = Files.createTempFile(Path.of(audioProcessingProperties.getTempDir()),
                    "voice-tag-" + tagEntity.getId() + "-", DOT + extension);
            outputPath = Files.createTempFile(Path.of(audioProcessingProperties.getTempDir()),
                    "voice-processed-" + songId + "-", DOT + extension);

            try (InputStream originalStream = storageService.download(songEntity.getOriginalS3Key())) {
                Files.copy(originalStream, originalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream tagStream = storageService.download(tagEntity.getS3Key())) {
                Files.copy(tagStream, tagPath, StandardCopyOption.REPLACE_EXISTING);
            }

            SongTagConfig config = songTagConfigMapper.toDomain(configEntity);
            audioProcessingService.insertVoiceTagAtInterval(originalPath, tagPath, outputPath, config);

            String processedKey = buildProcessedS3Key(userId, songId, extension);
            storageService.upload(processedKey, Files.newInputStream(outputPath), Files.size(outputPath),
                    resolveContentType(extension));

            persistenceService.markProcessed(songEntity, processedKey, audioProcessingService.extractMetadata(outputPath).durationSeconds());
            log.info("Voice processing completed: songId={}, processedKey={}", songId, processedKey);

        } catch (BusinessException ex) {
            persistenceService.markFailed(songId, "VOICE_006");
            throw new RuntimeException("Voice processing failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Voice processing failed: songId={}", songId, ex);
            persistenceService.markFailed(songId, "VOICE_006");
            throw new RuntimeException("Voice processing failed: " + ex.getMessage(), ex);
        } finally {
            deleteQuietly(originalPath);
            deleteQuietly(tagPath);
            deleteQuietly(outputPath);
        }
    }

    private String buildProcessedS3Key(UUID userId, UUID songId, String extension) {
        String prefix = voiceProperties.getStorage().getSongsProcessedPrefix();
        return prefix + KEY_DELIMITER + userId + KEY_DELIMITER + songId + DOT + extension;
    }

    private String resolveContentType(String extension) {
        switch (extension.toLowerCase()) {
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            default:
                return "application/octet-stream";
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("Failed to delete temp file: path={}", path, ex);
        }
    }
}

