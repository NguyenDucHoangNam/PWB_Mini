package com.pwb.voice.core.service;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.voice.core.exception.VoiceErrorCode;

import com.pwb.storage.api.StorageService;
import com.pwb.voice.api.VoiceTagFacade;
import com.pwb.voice.api.dto.request.CreateTtsVoiceTagRequest;
import com.pwb.voice.api.dto.request.UpdateVoiceTagRequest;
import com.pwb.voice.api.dto.request.UploadVoiceTagRequest;
import com.pwb.voice.api.dto.response.VoiceTagResponse;
import com.pwb.voice.api.enums.VoiceTagType;
import com.pwb.voice.core.model.VoiceTag;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import com.pwb.voice.infrastructure.persistence.mapper.VoiceTagMapper;
import com.pwb.voice.infrastructure.persistence.repository.VoiceTagJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagFacadeImpl implements VoiceTagFacade {

    private final VoiceTagService voiceTagService;
    private final VoiceTagJpaRepository voiceTagJpaRepository;
    private final VoiceTagMapper voiceTagMapper;
    private final StorageService storageService;

    @Override
    public VoiceTagResponse createTtsTag(UUID userId, CreateTtsVoiceTagRequest request) {
        VoiceTag domain = voiceTagService.createTtsTag(userId, request);
        return toResponse(domain);
    }

    @Override
    public VoiceTagResponse uploadTag(UUID userId, MultipartFile file, UploadVoiceTagRequest request) {
        VoiceTag domain = voiceTagService.uploadTag(userId, file, request);
        return toResponse(domain);
    }

    @Override
    public Page<VoiceTagResponse> listTags(UUID userId, VoiceTagType type, Pageable pageable) {
        Page<VoiceTagJpaEntity> entities = type == null
                ? voiceTagJpaRepository.findByUserIdAndDeletedFalse(userId, pageable)
                : voiceTagJpaRepository.findByUserIdAndTagTypeAndDeletedFalse(userId, type, pageable);

        return entities.map(this::toResponse);
    }

    @Override
    public VoiceTagResponse getTag(UUID userId, UUID tagId) {
        VoiceTagJpaEntity entity = voiceTagJpaRepository
                .findByIdAndUserIdAndDeletedFalse(tagId, userId)
                .orElseThrow(() -> new BusinessException(VoiceErrorCode.VOICE_TAG_NOT_FOUND));
        return toResponse(voiceTagMapper.toDomain(entity));
    }

    @Override
    public VoiceTagResponse updateTag(UUID userId, UUID tagId, UpdateVoiceTagRequest request) {
        VoiceTag domain = voiceTagService.updateTag(userId, tagId, request);
        return toResponse(domain);
    }

    @Override
    public void deleteTag(UUID userId, UUID tagId) {
        voiceTagService.deleteTag(userId, tagId);
    }

    @Override
    public URL getAudioPresignedUrl(UUID userId, UUID tagId, Duration expiration) {
        VoiceTagJpaEntity entity = voiceTagJpaRepository
                .findByIdAndUserIdAndDeletedFalse(tagId, userId)
                .orElseThrow(() -> new BusinessException(VoiceErrorCode.VOICE_TAG_NOT_FOUND));

        return storageService.generatePresignedUrl(entity.getS3Key(), expiration).getUrl();
    }

    private VoiceTagResponse toResponse(VoiceTag domain) {
        if (domain == null) {
            return null;
        }
        return VoiceTagResponse.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .name(domain.getName())
                .tagType(domain.getTagType())
                .sourceText(domain.getSourceText())
                .languageCode(domain.getLanguageCode())
                .durationSeconds(domain.getDurationSeconds())
                .fileSizeBytes(domain.getFileSizeBytes())
                .isDefault(domain.isDefault())
                .build();
    }

    private VoiceTagResponse toResponse(VoiceTagJpaEntity entity) {
        return toResponse(voiceTagMapper.toDomain(entity));
    }
}

