package com.pwb.backend.service.impl;

import com.pwb.backend.config.VoiceTagProperties;
import com.pwb.backend.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.dto.response.PreviewResponse;
import com.pwb.backend.dto.response.VoiceTagResponse;
import com.pwb.backend.dto.response.VoiceWhitelistResponse;
import com.pwb.backend.entity.rdbms.VoiceTag;
import com.pwb.backend.enums.MediaType;
import com.pwb.backend.enums.VoiceLanguage;
import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.exception.StorageException;
import com.pwb.backend.mapper.VoiceTagMapper;
import com.pwb.backend.repository.rdbms.VoiceTagRepository;
import com.pwb.backend.service.GcpTtsClient;
import com.pwb.backend.service.SsmlSanitizer;
import com.pwb.backend.service.VoiceQuotaService;
import com.pwb.backend.service.VoiceTagService;
import com.pwb.backend.config.StorageKey;
import com.pwb.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagServiceImpl implements VoiceTagService {

    private final VoiceTagRepository voiceTagRepository;
    private final VoiceQuotaService voiceQuotaService;
    private final SsmlSanitizer ssmlSanitizer;
    private final GcpTtsClient gcpTtsClient;
    private final StorageService storageService;
    private final VoiceTagMapper voiceTagMapper;
    private final VoiceTagProperties voiceTagProperties;

    @Override
    @Transactional(readOnly = true)
    public List<VoiceTagResponse> listForOwner(UUID ownerId) {
        return voiceTagMapper.toResponseList(
                voiceTagRepository.findAllByOwnerIdOrderByIsDefaultDescCreatedAtDesc(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceWhitelistResponse getWhitelist(String languageCode) {
        VoiceLanguage language = VoiceLanguage.fromCode(languageCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_LANGUAGE_CODE,
                        languageCode));

        return VoiceWhitelistResponse.builder()
                .languageCode(language.getCode())
                .voices(voiceTagMapper.toVoiceOptions(language.listVoices()))
                .build();
    }

    @Override
    @Transactional
    public VoiceTagResponse create(UUID ownerId, String actor, CreateVoiceTagRequest request) {
        validateTextLength(request.getTextContent());

        VoiceLanguage language = VoiceLanguage.fromCode(request.getLanguageCode())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_LANGUAGE_CODE,
                        request.getLanguageCode()));

        if (!language.supportsVoice(request.getVoiceName())) {
            throw new BusinessException(ErrorCode.INVALID_VOICE_NAME);
        }

        voiceQuotaService.preCheckActiveQuota(ownerId);
        voiceQuotaService.preCheckDailyQuota(ownerId);

        String ssml = ssmlSanitizer.sanitize(request.getTextContent());
        validateRawTextLength(ssmlSanitizer.stripSsmlToRawText(ssml));

        byte[] audioBytes;
        StorageKey storageKey;
        try {
            audioBytes = gcpTtsClient.synthesizeSsml(ssml, language.getCode(), request.getVoiceName());
            storageKey = storageService.upload(MediaType.VOICE_TAG, audioBytes, "mp3");
        } catch (BusinessException ex) {
            voiceQuotaService.rollbackActiveQuota(ownerId);
            throw ex;
        } catch (StorageException ex) {
            voiceQuotaService.rollbackActiveQuota(ownerId);
            throw ex;
        } catch (Exception ex) {
            voiceQuotaService.rollbackActiveQuota(ownerId);
            log.error("Unexpected error during voice tag creation: ownerId={}", ownerId, ex);
            throw new BusinessException(
                    ErrorCode.TTS_SERVICE_FAILED,
                    "Voice tag creation failed: " + ex.getMessage(),
                    ex);
        }

        try {
            VoiceTag tag = VoiceTag.builder()
                    .ownerId(ownerId)
                    .textContent(request.getTextContent())
                    .voiceName(request.getVoiceName())
                    .languageCode(language.getCode())
                    .s3Key(storageKey.getFullPath())
                    .fileSize((int) storageKey.getFileSize())
                    .isDefault(false)
                    .build();

            VoiceTag persisted = voiceTagRepository.save(tag);
            log.info("Created voice tag: id={}, ownerId={}, fileSize={}", persisted.getId(), ownerId, persisted.getFileSize());
            return voiceTagMapper.toResponse(persisted);
        } catch (Exception ex) {
            log.error("DB persist failed, rolling back S3 upload: ownerId={}, s3Key={}", ownerId, storageKey.getFullPath(), ex);
            try {
                storageService.deleteByPath(storageKey.getFullPath());
            } catch (Exception cleanupEx) {
                log.error("Failed to cleanup S3 object after DB error: s3Key={}", storageKey.getFullPath(), cleanupEx);
            }
            voiceQuotaService.rollbackActiveQuota(ownerId);
            throw ex;
        }
    }

    @Override
    @Transactional
    public VoiceTagResponse setDefault(UUID ownerId, String actor, UUID tagId) {
        VoiceTag tag = findOwnedOrThrow(ownerId, tagId);

        if (tag.isDefault()) {
            throw new BusinessException(ErrorCode.VOICE_TAG_ALREADY_DEFAULT);
        }

        voiceTagRepository.clearDefaultForOwner(ownerId, tagId, actor);
        tag.setDefault(true);

        try {
            VoiceTag persisted = voiceTagRepository.save(tag);
            log.info("Set default voice tag: id={}, ownerId={}", tagId, ownerId);
            return voiceTagMapper.toResponse(persisted);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition on set default: ownerId={}, tagId={}", ownerId, tagId);
            throw new BusinessException(ErrorCode.VOICE_TAG_ALREADY_DEFAULT, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewResponse generatePreview(UUID ownerId, UUID tagId) {
        VoiceTag tag = findOwnedOrThrow(ownerId, tagId);

        Duration ttl = Duration.ofSeconds(voiceTagProperties.getPreviewUrlTtlSeconds());
        String preSignedUrl = storageService.generatePresignedGetUrl(tag.getS3Key(), ttl);

        return PreviewResponse.builder()
                .preSignedUrl(preSignedUrl)
                .build();
    }

    @Override
    @Transactional
    public void delete(UUID ownerId, UUID tagId) {
        VoiceTag tag = findOwnedOrThrow(ownerId, tagId);
        String s3Key = tag.getS3Key();

        voiceTagRepository.delete(tag);

        try {
            storageService.deleteByPath(s3Key);
        } catch (Exception ex) {
            log.error("VOICE_TAG_S3_DELETE_FAILED: tagId={}, ownerId={}, s3Key={}", tagId, ownerId, s3Key, ex);
        }

        voiceQuotaService.decrementActiveQuota(ownerId);
        log.info("Deleted voice tag: id={}, ownerId={}", tagId, ownerId);
    }

    private VoiceTag findOwnedOrThrow(UUID ownerId, UUID tagId) {
        return voiceTagRepository.findByIdAndOwnerId(tagId, ownerId)
                .orElseThrow(() -> {
                    boolean exists = voiceTagRepository.findById(tagId).isPresent();
                    if (exists) {
                        log.warn("IDOR_ATTEMPT: ownerId={} tried to access tagId={} owned by another user", ownerId, tagId);
                        return new BusinessException(ErrorCode.VOICE_TAG_FORBIDDEN);
                    }
                    return new BusinessException(ErrorCode.VOICE_TAG_NOT_FOUND);
                });
    }

    private void validateTextLength(String text) {
        if (text != null && text.length() > voiceTagProperties.getMaxTextLength()) {
            throw new BusinessException(
                    ErrorCode.TTS_TEXT_TOO_LONG,
                    voiceTagProperties.getMaxTextLength());
        }
    }

    private void validateRawTextLength(String rawText) {
        if (rawText != null && rawText.length() > voiceTagProperties.getMaxRawTextLength()) {
            throw new BusinessException(
                    ErrorCode.TTS_TEXT_TOO_LONG,
                    voiceTagProperties.getMaxRawTextLength());
        }
    }
}
