package com.pwb.voice.core.service;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;
import com.pwb.storage.api.StorageErrorCode;
import com.pwb.storage.api.StorageService;
import com.pwb.voice.api.dto.request.CreateTtsVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.voice.api.dto.request.UploadVoiceTagRequest;
import com.pwb.voice.core.model.VoiceTag;
import com.pwb.voice.infrastructure.config.VoiceProperties;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.voice.infrastructure.persistence.mapper.VoiceTagMapper;
import com.pwb.voice.infrastructure.persistence.repository.SongTagConfigJpaRepository;
import com.pwb.voice.infrastructure.persistence.repository.VoiceTagJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagServiceImpl implements VoiceTagService {

    private static final String MP3_EXTENSION = "mp3";
    private static final String AUDIO_MPEG = "audio/mpeg";
    private static final String KEY_DELIMITER = "/";
    private static final String DOT = ".";
    private static final long SECONDS_PER_MEGABYTE_MP3_128KBPS = 8L;
    private static final long MEGABYTE = 1_000_000L;
    private static final String DELETED_BY_SYSTEM = "system";

    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final SongTagConfigJpaRepository songTagConfigJpaRepository;
    private final VoiceTagMapper voiceTagMapper;
    private final StorageService storageService;
    private final TextToSpeechService textToSpeechService;
    private final AudioFileValidator audioFileValidator;
    private final VoiceProperties voiceProperties;

    @Override
    public VoiceTag createTtsTag(UUID userId, CreateTtsVoiceTagRequest request) {
        log.info("Creating TTS voice tag: userId={}, languageCode={}", userId, request.getLanguageCode());

        assertNameAvailable(userId, request.getName(), null);

        UUID tagId = UUID.randomUUID();
        String s3Key = buildS3Key(userId, tagId, MP3_EXTENSION);

        byte[] audioBytes = textToSpeechService.synthesize(request.getText(), request.getLanguageCode());
        Integer estimatedDuration = estimateMp3DurationSeconds(audioBytes.length);

        VoiceTag domain = VoiceTag.createTtsTag(
                userId,
                request.getName(),
                request.getText(),
                request.getLanguageCode(),
                s3Key,
                estimatedDuration,
                (long) audioBytes.length
        );

        storageService.upload(s3Key, audioBytes, AUDIO_MPEG);

        VoiceTagJpaEntity entity = voiceTagMapper.toEntity(domain);
        VoiceTagJpaEntity saved = voiceTagJpaRepository.save(entity);

        log.info("TTS voice tag created: userId={}, tagId={}, durationSeconds={}, fileSizeBytes={}",
                userId, saved.getId(), estimatedDuration, audioBytes.length);

        return voiceTagMapper.toDomain(saved);
    }

    @Override
    public VoiceTag uploadTag(UUID userId, MultipartFile file, UploadVoiceTagRequest request) {
        log.info("Uploading voice tag: userId={}, filename={}, sizeBytes={}",
                userId, safeFileName(file), safeSize(file));

        String extension = extractExtension(file);
        audioFileValidator.validateExtension(extension);
        audioFileValidator.validateSize(safeSize(file));

        AudioMetadata metadata;
        try (InputStream probeStream = file.getInputStream()) {
            audioFileValidator.validateMagicBytes(probeStream, extension);
        } catch (IOException ex) {
            throw new BusinessException(VoiceErrorCode.AUDIO_PROCESSING_FAILED);
        }

        try (InputStream durationStream = file.getInputStream()) {
            metadata = audioFileValidator.validateDuration(durationStream, extension);
        } catch (IOException ex) {
            throw new BusinessException(VoiceErrorCode.AUDIO_PROCESSING_FAILED);
        }

        assertNameAvailable(userId, request.getName(), null);

        UUID tagId = UUID.randomUUID();
        String s3Key = buildS3Key(userId, tagId, extension);

        try (InputStream uploadStream = file.getInputStream()) {
            storageService.upload(s3Key, uploadStream, file.getSize(), resolveContentType(extension));
        } catch (IOException ex) {
            throw new BusinessException(StorageErrorCode.STORAGE_UPLOAD_FAILED);
        }

        VoiceTag domain = VoiceTag.createUploadedTag(
                userId,
                request.getName(),
                s3Key,
                metadata.durationSeconds(),
                file.getSize()
        );

        VoiceTagJpaEntity entity = voiceTagMapper.toEntity(domain);
        VoiceTagJpaEntity saved = voiceTagJpaRepository.save(entity);

        log.info("Voice tag uploaded: userId={}, tagId={}, durationSeconds={}, fileSizeBytes={}",
                userId, saved.getId(), metadata.durationSeconds(), file.getSize());

        return voiceTagMapper.toDomain(saved);
    }

    @Override
    public VoiceTag updateTag(UUID userId, UUID tagId, UpdateVoiceTagRequest request) {
        log.info("Updating voice tag: userId={}, tagId={}", userId, tagId);

        VoiceTagJpaEntity existing = voiceTagJpaRepository
                .findByIdAndUserIdAndDeletedFalse(tagId, userId)
                .orElseThrow(() -> new BusinessException(VoiceErrorCode.VOICE_TAG_NOT_FOUND));

        if (request.getName() != null && !request.getName().isBlank()) {
            assertNameAvailable(userId, request.getName(), tagId);
        }

        VoiceTag domain = voiceTagMapper.toDomain(existing);
        domain.updateMetadata(request.getName());

        VoiceTagJpaEntity merged = voiceTagMapper.toEntity(domain, existing);
        VoiceTagJpaEntity saved = voiceTagJpaRepository.save(merged);

        log.info("Voice tag updated: userId={}, tagId={}", userId, saved.getId());

        return voiceTagMapper.toDomain(saved);
    }

    @Override
    public void deleteTag(UUID userId, UUID tagId) {
        log.info("Deleting voice tag: userId={}, tagId={}", userId, tagId);

        VoiceTagJpaEntity entity = voiceTagJpaRepository
                .findByIdAndUserIdAndDeletedFalse(tagId, userId)
                .orElseThrow(() -> new BusinessException(VoiceErrorCode.VOICE_TAG_NOT_FOUND));

        if (songTagConfigJpaRepository.existsByVoiceTagIdAndDeletedFalse(tagId)) {
            log.warn("Cannot delete voice tag in use: userId={}, tagId={}", userId, tagId);
            throw new BusinessException(VoiceErrorCode.VOICE_TAG_IN_USE);
        }

        entity.markDeleted(DELETED_BY_SYSTEM);
        voiceTagJpaRepository.save(entity);

        try {
            storageService.delete(entity.getS3Key());
            log.info("S3 object deleted for voice tag: tagId={}, s3Key={}", tagId, entity.getS3Key());
        } catch (RuntimeException ex) {
            log.warn("S3 cleanup failed for voice tag: tagId={}, s3Key={}", tagId, entity.getS3Key(), ex);
        }
    }

    private void assertNameAvailable(UUID userId, String name, UUID excludeTagId) {
        boolean exists = excludeTagId == null
                ? voiceTagJpaRepository.existsByUserIdAndNameAndDeletedFalse(userId, name)
                : voiceTagJpaRepository.existsByUserIdAndNameAndIdNotAndDeletedFalse(userId, name, excludeTagId);

        if (exists) {
            throw new BusinessException(VoiceErrorCode.DUPLICATE_VOICE_TAG_NAME);
        }
    }

    private String buildS3Key(UUID userId, UUID tagId, String extension) {
        String prefix = voiceProperties.getStorage().getVoiceTagsPrefix();
        return prefix + KEY_DELIMITER + userId + KEY_DELIMITER + tagId + DOT + extension;
    }

    private Integer estimateMp3DurationSeconds(long bytes) {
        long seconds = (bytes / MEGABYTE) * SECONDS_PER_MEGABYTE_MP3_128KBPS;
        return (int) Math.max(seconds, 0L);
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
                return "audio/wav";
            case "flac":
                return "audio/flac";
            default:
                return "application/octet-stream";
        }
    }

    private long safeSize(MultipartFile file) {
        return file == null ? 0L : file.getSize();
    }

    private String safeFileName(MultipartFile file) {
        return file == null ? "<null>" : file.getOriginalFilename();
    }
}
