package com.pwb.backend.modules.voice_tag.service.impl;

import org.springframework.dao.DataIntegrityViolationException;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagPreviewResponse;
import com.pwb.backend.modules.voice_tag.dto.response.VoiceTagResponse;
import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import com.pwb.backend.modules.voice_tag.service.SanitizedSsml;
import com.pwb.backend.modules.voice_tag.service.VoiceTagDailyQuotaService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagGcpTtsClient;
import com.pwb.backend.modules.voice_tag.service.VoiceTagOwnershipService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagQuotaService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagS3CompensationService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagSsmlSanitizer;
import com.pwb.backend.modules.voice_tag.service.VoiceTagStorageQuotaService;
import com.pwb.backend.modules.voice_tag.service.VoiceTagVoiceWhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagServiceImpl implements VoiceTagService {

    private static final String S3_KEY_PREFIX = "voicetags/";
    private static final String S3_KEY_SUFFIX = ".mp3";
    private static final String CONTENT_TYPE_MPEG = "audio/mpeg";
    private static final int ESTIMATED_TTS_AVERAGE_BYTES = 20 * 1024;

    private final VoiceTagRepository voiceTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VoiceTagSsmlSanitizer ssmlSanitizer;
    private final VoiceTagVoiceWhitelistService voiceWhitelistService;
    private final VoiceTagDailyQuotaService dailyQuotaService;
    private final VoiceTagQuotaService quotaService;
    private final VoiceTagStorageQuotaService storageQuotaService;
    private final VoiceTagGcpTtsClient gcpTtsClient;
    private final VoiceTagOwnershipService ownershipService;
    private final VoiceTagS3CompensationService compensationService;
    private final VoiceTagProperties properties;
    private final VoiceTagTxService txService;

    @Override
    public VoiceTagResponse create(UUID ownerId, CreateVoiceTagRequest request) {
        SanitizedSsml sanitized = ssmlSanitizer.sanitize(request.textContent());
        voiceWhitelistService.validate(request.voiceName(), request.languageCode());

        try {
            dailyQuotaService.checkAndIncrement(ownerId);
        } catch (BusinessException ex) {
            quotaService.release(ownerId);
            throw ex;
        }

        quotaService.tryReserve(ownerId);
        storageQuotaService.checkCapacity(ownerId, ESTIMATED_TTS_AVERAGE_BYTES);

        byte[] mp3;
        try {
            mp3 = gcpTtsClient.synthesize(sanitized.ssml(), request.languageCode(), request.voiceName());
        } catch (RuntimeException ex) {
            quotaService.release(ownerId);
            throw ex;
        }

        String s3Key = S3_KEY_PREFIX + UUID.randomUUID() + S3_KEY_SUFFIX;
        UUID tagId = UUID.randomUUID();
        try {
            objectStorageService.putObject(
                    StorageBucket.VOICE_TAG, s3Key, mp3, CONTENT_TYPE_MPEG,
                    Map.of("owner-id", ownerId.toString(), "tag-id", tagId.toString()));
        } catch (RuntimeException ex) {
            quotaService.release(ownerId);
            throw ex;
        }

        VoiceTag tag = new VoiceTag();
        tag.setId(tagId);
        tag.setOwnerId(ownerId);
        tag.setTextContent(sanitized.ssml());
        tag.setVoiceName(request.voiceName());
        tag.setLanguageCode(request.languageCode());
        tag.setS3Key(s3Key);
        tag.setFileSize(mp3.length);
        tag.setDefault(false);
        tag.setDeleted(false);

        try {
            quotaService.postCheckAndCommit(ownerId);
            txService.persistNew(tag);
        } catch (RuntimeException ex) {
            log.warn("VOICE_TAG_DB_COMMIT_FAILED s3Key={} tagId={} reason={}",
                    s3Key, tagId, ex.getMessage());
            compensationService.cleanupS3Async(s3Key, ownerId, tagId, 1, properties.getCompensation().getMaxAttempts());
            quotaService.release(ownerId);
            throw ex;
        }

        log.info("VOICE_TAG_CREATED id={} s3Key={} bytes={} ownerId={}",
                tagId, s3Key, mp3.length, ownerId);
        return VoiceTagResponse.fromEntity(tag);
    }

    @Override
    public VoiceTagPreviewResponse generatePreviewUrl(UUID ownerId, UUID tagId) {
        VoiceTag tag = ownershipService.loadActiveOrThrow(tagId, ownerId);
        String url = objectStorageService.generatePresignedDownloadUrl(
                StorageBucket.VOICE_TAG, tag.getS3Key(),
                Duration.ofSeconds(properties.getPreviewUrlTtlSeconds()));
        return new VoiceTagPreviewResponse(url);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceTagResponse> list(UUID ownerId) {
        return voiceTagRepository.findAllActiveByOwner(ownerId).stream()
                .map(VoiceTagResponse::fromEntity)
                .toList();
    }

    @Override
    public void setDefault(UUID ownerId, UUID tagId) {
        VoiceTag tag = ownershipService.loadActiveOrThrow(tagId, ownerId);
        try {
            txService.setDefaultAtomic(tag.getId(), ownerId);
        } catch (DataIntegrityViolationException ex) {
            log.warn("VOICE_TAG_DEFAULT_RACE_CONFLICT tagId={} ownerId={}", tag.getId(), ownerId);
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_ALREADY_DEFAULT);
        }
    }

    @Override
    public void softDelete(UUID ownerId, UUID tagId) {
        VoiceTag tag = ownershipService.loadActiveOrThrow(tagId, ownerId);
        long activeDemos = txService.countActiveDemosReferencing(tag.getId());
        if (activeDemos > 0L) {
            log.warn("VOICE_TAG_IN_USE tagId={} activeDemoCount={}", tag.getId(), activeDemos);
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_IN_USE);
        }

        txService.markSoftDeleted(tag.getId());
        storageDeleteWithCompensation(tag);
    }

    @Override
    public void restore(UUID ownerId, UUID tagId) {
        VoiceTag tag = ownershipService.loadIncludingDeletedOrThrow(tagId, ownerId);
        if (!tag.isDeleted()) {
            return;
        }
        if (!txService.isWithinRestoreWindow(tag)) {
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_NOT_FOUND);
        }
        storageQuotaService.checkCapacity(ownerId, tag.getFileSize());
        txService.markRestored(tag.getId());
    }

    private void storageDeleteWithCompensation(VoiceTag tag) {
        try {
            objectStorageService.deleteObject(StorageBucket.VOICE_TAG, tag.getS3Key());
            log.warn("VOICE_TAG_S3_DELETED tagId={} s3Key={}", tag.getId(), tag.getS3Key());
        } catch (RuntimeException ex) {
            log.warn("VOICE_TAG_S3_DELETE_FAILED tagId={} s3Key={} reason={}",
                    tag.getId(), tag.getS3Key(), ex.getMessage());
            compensationService.cleanupS3Async(
                    tag.getS3Key(), tag.getOwnerId(), tag.getId(), 1,
                    properties.getCompensation().getMaxAttempts());
        }
    }
}
