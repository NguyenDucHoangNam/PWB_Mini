package com.pwb.voice.core.service;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.outbox.api.OutboxEnqueueRequested;
import com.pwb.outbox.api.OutboxEventPayload;
import com.pwb.outbox.api.OutboxWriter;
import com.pwb.outbox.infrastructure.messaging.OutboxKafkaConfig;
import com.pwb.storage.api.StorageService;
import com.pwb.voice.api.dto.request.ConfigureVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateSongRequest;
import com.pwb.voice.api.dto.request.UploadSongRequest;
import com.pwb.voice.api.enums.SongStatus;
import com.pwb.voice.api.event.VoiceProcessingRequestedIntegrationEvent;
import com.pwb.voice.core.model.Song;
import com.pwb.voice.core.model.SongTagConfig;
import com.pwb.voice.infrastructure.config.VoiceProperties;
import com.pwb.voice.infrastructure.persistence.entity.SongJpaEntity;
import com.pwb.voice.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.voice.infrastructure.persistence.mapper.SongMapper;
import com.pwb.voice.infrastructure.persistence.mapper.SongTagConfigMapper;
import com.pwb.voice.infrastructure.persistence.repository.SongJpaRepository;
import com.pwb.voice.infrastructure.persistence.repository.SongTagConfigJpaRepository;
import com.pwb.voice.infrastructure.persistence.repository.VoiceTagJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private static final String KEY_DELIMITER = "/";
    private static final String DOT = ".";
    private static final String MP3_EXTENSION = "mp3";
    private static final String AUDIO_MPEG = "audio/mpeg";
    private static final String AUDIO_WAV = "audio/wav";
    private static final String AUDIO_FLAC = "audio/flac";
    private static final String DELETED_BY_SYSTEM = "system";

    private final SongJpaRepository songJpaRepository;
    private final SongTagConfigJpaRepository songTagConfigJpaRepository;
    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final SongMapper songMapper;
    private final SongTagConfigMapper songTagConfigMapper;
    private final StorageService storageService;
    private final AudioFileValidator audioFileValidator;
    private final VoiceProperties voiceProperties;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

@Override
@Transactional
public Song uploadSong(UUID userId, MultipartFile file, UploadSongRequest request) {
        log.info("Uploading song: userId={}, title={}, sizeBytes={}",
                userId, request.getTitle(), safeSize(file));

        String extension = extractExtension(file);
        audioFileValidator.validateExtension(extension);
        audioFileValidator.validateSize(safeSize(file));

        try (InputStream probeStream = file.getInputStream()) {
            audioFileValidator.validateMagicBytes(probeStream, extension);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.AUDIO_PROCESSING_FAILED);
        }

        AudioMetadata metadata;
        try (InputStream durationStream = file.getInputStream()) {
            metadata = audioFileValidator.validateDuration(durationStream, extension);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.AUDIO_PROCESSING_FAILED);
        }

        UUID songId = UUID.randomUUID();
        String s3Key = buildOriginalS3Key(userId, songId, extension);

        try (InputStream uploadStream = file.getInputStream()) {
            storageService.upload(s3Key, uploadStream, file.getSize(), resolveContentType(extension));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }

        Song domain = Song.create(
                userId,
                request.getTitle(),
                request.getArtist(),
                request.getAlbum(),
                s3Key,
                file.getSize(),
                metadata.durationSeconds(),
                extension
        );

        SongJpaEntity entity = songMapper.toEntity(domain);
        SongJpaEntity saved = songJpaRepository.save(entity);

        log.info("Song uploaded: userId={}, songId={}, durationSeconds={}, fileSizeBytes={}",
                userId, saved.getId(), metadata.durationSeconds(), file.getSize());

        return songMapper.toDomain(saved);
    }

    @Override
    public Page<Song> listSongs(UUID userId, SongStatus status, Pageable pageable) {
        if (status == null) {
            return songJpaRepository
                    .findByUserIdAndDeletedFalse(userId, pageable)
                    .map(songMapper::toDomain);
        }
        return songJpaRepository
                .findByUserIdAndStatusAndDeletedFalse(userId, status, pageable)
                .map(songMapper::toDomain);
    }

    @Override
    public Song getSong(UUID userId, UUID songId) {
        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
        return songMapper.toDomain(entity);
    }

@Override
@Transactional
public Song updateSong(UUID userId, UUID songId, UpdateSongRequest request) {
        log.info("Updating song: userId={}, songId={}", userId, songId);

        SongJpaEntity existing = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        Song domain = songMapper.toDomain(existing);
        domain.updateMetadata(request.getTitle(), request.getArtist(), request.getAlbum());

        SongJpaEntity merged = songMapper.toEntity(domain, existing);
        SongJpaEntity saved = songJpaRepository.save(merged);

        log.info("Song updated: userId={}, songId={}", userId, saved.getId());

        return songMapper.toDomain(saved);
    }

@Override
@Transactional
public void deleteSong(UUID userId, UUID songId) {
        log.info("Deleting song: userId={}, songId={}", userId, songId);

        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        String originalKey = entity.getOriginalS3Key();
        String processedKey = entity.getProcessedS3Key();

        entity.markDeleted(DELETED_BY_SYSTEM);
        songJpaRepository.save(entity);

        try {
            storageService.delete(originalKey);
            log.info("S3 object deleted (original) for song: songId={}, s3Key={}", songId, originalKey);
        } catch (RuntimeException ex) {
            log.warn("S3 cleanup failed (original) for song: songId={}, s3Key={}", songId, originalKey, ex);
        }

        if (processedKey != null) {
            try {
                storageService.delete(processedKey);
                log.info("S3 object deleted (processed) for song: songId={}, s3Key={}", songId, processedKey);
            } catch (RuntimeException ex) {
                log.warn("S3 cleanup failed (processed) for song: songId={}, s3Key={}", songId, processedKey, ex);
            }
        }
    }

@Override
@Transactional
public SongTagConfig configureVoiceTag(UUID userId, UUID songId, ConfigureVoiceTagRequest request) {
        log.info("Configuring voice tag: userId={}, songId={}, voiceTagId={}",
                userId, songId, request.getVoiceTagId());

        SongJpaEntity songEntity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        VoiceTagJpaEntity voiceTagEntity = voiceTagJpaRepository
                .findByIdAndUserIdAndDeletedFalse(request.getVoiceTagId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOICE_TAG_NOT_FOUND));

        SongTagConfigJpaEntity configEntity = songTagConfigJpaRepository
                .findBySongIdAndDeletedFalse(songId)
                .orElse(null);

        SongTagConfig domain;
        SongTagConfigJpaEntity targetEntity;
        if (configEntity == null) {
            domain = SongTagConfig.create(
                    songId,
                    request.getVoiceTagId(),
                    request.getIntervalSeconds(),
                    request.getVolumePercentage(),
                    request.getFadeInDurationMs(),
                    request.getFadeOutDurationMs(),
                    request.getStartOffsetSeconds()
            );
            targetEntity = songTagConfigMapper.toEntity(domain);
        } else {
            domain = songTagConfigMapper.toDomain(configEntity);
            domain.updateParams(
                    request.getVoiceTagId(),
                    request.getIntervalSeconds(),
                    request.getVolumePercentage(),
                    request.getFadeInDurationMs(),
                    request.getFadeOutDurationMs(),
                    request.getStartOffsetSeconds(),
                    request.getEnabled()
            );
            targetEntity = songTagConfigMapper.toEntity(domain, configEntity);
        }

        SongTagConfigJpaEntity saved = songTagConfigJpaRepository.save(targetEntity);
        log.info("Voice tag configured: userId={}, songId={}, configId={}",
                userId, songId, saved.getId());

        return songTagConfigMapper.toDomain(saved);
    }

    @Override
    public SongTagConfig getVoiceTagConfig(UUID userId, UUID songId) {
        if (!songJpaRepository.existsByIdAndUserIdAndDeletedFalse(songId, userId)) {
            throw new BusinessException(ErrorCode.SONG_NOT_FOUND);
        }
        SongTagConfigJpaEntity entity = songTagConfigJpaRepository
                .findBySongIdAndDeletedFalse(songId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
        return songTagConfigMapper.toDomain(entity);
    }

@Override
@Transactional
public void removeVoiceTagConfig(UUID userId, UUID songId) {
        log.info("Removing voice tag config: userId={}, songId={}", userId, songId);

        if (!songJpaRepository.existsByIdAndUserIdAndDeletedFalse(songId, userId)) {
            throw new BusinessException(ErrorCode.SONG_NOT_FOUND);
        }

        SongTagConfigJpaEntity entity = songTagConfigJpaRepository
                .findBySongIdAndDeletedFalse(songId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        entity.markDeleted(DELETED_BY_SYSTEM);
        songTagConfigJpaRepository.save(entity);

        log.info("Voice tag config removed: userId={}, songId={}", userId, songId);
    }

@Override
@Transactional
public Song triggerProcessing(UUID userId, UUID songId) {
        log.info("Triggering processing: userId={}, songId={}", userId, songId);

        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdForUpdate(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        SongStatus current = entity.getStatus();
        if (current == SongStatus.PROCESSED) {
            throw new BusinessException(ErrorCode.SONG_ALREADY_PROCESSED);
        }
        if (current == SongStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Song domain = songMapper.toDomain(entity);
        domain.markProcessing();

        SongJpaEntity merged = songMapper.toEntity(domain, entity);
        SongJpaEntity saved = songJpaRepository.save(merged);

        publishProcessingEvent(songId, userId);

        log.info("Song processing triggered: userId={}, songId={}, status={}", userId, saved.getId(), saved.getStatus());

        return songMapper.toDomain(saved);
    }

    private void publishProcessingEvent(UUID songId, UUID userId) {
        VoiceProcessingRequestedIntegrationEvent event = new VoiceProcessingRequestedIntegrationEvent(
                UUID.randomUUID().toString(),
                songId,
                userId,
                Instant.now()
        );
        try {
            String body = objectMapper.writeValueAsString(event);
            OutboxEventPayload payload = OutboxEventPayload.of(body);
            applicationEventPublisher.publishEvent(
                    new OutboxEnqueueRequested(
                            OutboxKafkaConfig.TOPIC_VOICE_PROCESSING,
                            songId.toString(),
                            "Song",
                            payload,
                            Map.of()
                    )
            );
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize voice processing event: songId={}", songId, ex);
            throw new BusinessException(ErrorCode.AUDIO_PROCESSING_FAILED);
        }
    }

    @Override
    public Song getProcessingStatus(UUID userId, UUID songId) {
        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
        return songMapper.toDomain(entity);
    }

    @Override
    public String getStreamPresignedKey(UUID userId, UUID songId) {
        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));

        String key = entity.getProcessedS3Key() != null
                ? entity.getProcessedS3Key()
                : entity.getOriginalS3Key();

        if (key == null) {
            throw new BusinessException(ErrorCode.SONG_NOT_READY);
        }
        return key;
    }

    @Override
    public String getOriginalPresignedKey(UUID userId, UUID songId) {
        SongJpaEntity entity = songJpaRepository
                .findByIdAndUserIdAndDeletedFalse(songId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
        return entity.getOriginalS3Key();
    }

    private String buildOriginalS3Key(UUID userId, UUID songId, String extension) {
        String prefix = voiceProperties.getStorage().getSongsOriginalPrefix();
        return prefix + KEY_DELIMITER + userId + KEY_DELIMITER + songId + DOT + extension;
    }

    private String extractExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) {
            return "";
        }
        int dot = original.lastIndexOf(DOT);
        if (dot < 0 || dot == original.length() - 1) {
            return "";
        }
        return original.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String extension) {
        switch (extension) {
            case MP3_EXTENSION:
                return AUDIO_MPEG;
            case "wav":
                return AUDIO_WAV;
            case "flac":
                return AUDIO_FLAC;
            default:
                return "application/octet-stream";
        }
    }

    private long safeSize(MultipartFile file) {
        return file == null ? 0L : file.getSize();
    }
}
