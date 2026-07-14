package com.pwb.backend.modules.audio.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.audio.config.AudioProperties;
import com.pwb.backend.modules.audio.dto.request.ConfirmUploadRequest;
import com.pwb.backend.modules.audio.dto.request.PresignedUrlRequest;
import com.pwb.backend.modules.audio.dto.response.ConfirmUploadResponse;
import com.pwb.backend.modules.audio.dto.response.DemoListItemResponse;
import com.pwb.backend.modules.audio.dto.response.DemoStatusResponse;
import com.pwb.backend.modules.audio.dto.response.PresignedUrlResponse;
import com.pwb.backend.modules.audio.dto.response.RotateKeyResponse;
import com.pwb.backend.modules.audio.entity.AudioProcessingJob;
import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.AudioFormat;
import com.pwb.backend.modules.audio.event.AudioProcessingEvent;
import com.pwb.backend.modules.audio.event.AudioProcessingEventPublisher;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.mapper.DemoMapper;
import com.pwb.backend.modules.audio.repository.AudioProcessingJobRepository;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.audio.service.AesKeyRotationService;
import com.pwb.backend.modules.audio.service.DemoQuotaService;
import com.pwb.backend.modules.audio.service.DemoService;
import com.pwb.backend.modules.audio.service.UploadClaimService;
import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoServiceImpl implements DemoService {

    private static final String S3_KEY_ORIGINAL_PREFIX = "original/";
    private static final String S3_KEY_ORIGINAL_CONFIRMED_PREFIX = "original/confirmed/";
    private static final String S3_KEY_STREAM_PREFIX = "stream/";

    private final ObjectStorageService objectStorageService;
    private final DemoRepository demoRepository;
    private final AudioProcessingJobRepository jobRepository;
    private final DemoMapper demoMapper;
    private final UploadClaimService uploadClaimService;
    private final DemoQuotaService demoQuotaService;
    private final AudioProcessingEventPublisher audioProcessingEventPublisher;
    private final AudioProperties audioProperties;
    private final AesKeyRotationService aesKeyRotationService;
    private final VoiceTagRepository voiceTagRepository;

    @Override
    @Transactional
    public PresignedUrlResponse generatePresignedUploadUrl(UUID ownerId, PresignedUrlRequest request) {
        validateRequest(request);

        AudioFormat format = AudioFormat.fromContentType(request.contentType());
        if (format == null) {
            throw new BusinessException(AudioErrorCode.UNSUPPORTED_AUDIO_FORMAT,
                    "Unsupported content type: " + request.contentType());
        }

        long maxSize = audioProperties.getMaxFileSizeBytes();
        if (request.fileSize() > maxSize) {
            throw new BusinessException(AudioErrorCode.FILE_SIZE_MISMATCH,
                    "Requested fileSize " + request.fileSize() + " exceeds max " + maxSize);
        }

        String s3Key = S3_KEY_ORIGINAL_PREFIX + ownerId + "/"
                + UUID.randomUUID() + "." + format.extension();

        ObjectStorageService.PresignedUpload presigned = objectStorageService.generatePresignedUpload(
                StorageBucket.DEMO_AUDIO,
                s3Key,
                format.contentType(),
                request.fileSize(),
                Duration.ofSeconds(audioProperties.getPresigned().getExpirySeconds()));

        uploadClaimService.save(
                presigned.key(),
                ownerId,
                request.contentType(),
                request.fileSize(),
                Duration.ofSeconds(audioProperties.getPresigned().getUploadClaimTtlSeconds()));

        log.info("PRESIGNED_UPLOAD_GENERATED demoKey={} userId={} contentType={} fileSize={} expiresIn={}",
                presigned.key(), ownerId, request.contentType(), request.fileSize(),
                presigned.expiresInSeconds());

        return new PresignedUrlResponse(
                presigned.url(),
                presigned.key(),
                presigned.expiresInSeconds(),
                Instant.now(),
                maxSize,
                request.contentType());
    }

    @Override
    @Transactional
    public ConfirmUploadResponse confirmUpload(UUID ownerId, ConfirmUploadRequest request) {
        UploadClaimService.UploadClaim claim = uploadClaimService.load(request.s3Key());
        if (claim == null) {
            throw new BusinessException(AudioErrorCode.FILE_NOT_FOUND_ON_S3,
                    "Upload claim not found for s3Key=" + request.s3Key());
        }
        if (!claim.userId().equals(ownerId)) {
            throw new BusinessException(AudioErrorCode.INVALID_S3_KEY_OWNER,
                    "s3Key " + request.s3Key() + " does not belong to userId=" + ownerId);
        }

        ObjectStorageService.StoredObjectMetadata metadata;
        try {
            metadata = objectStorageService.getMetadata(StorageBucket.DEMO_AUDIO, request.s3Key());
        } catch (Exception ex) {
            throw new BusinessException(AudioErrorCode.FILE_NOT_FOUND_ON_S3,
                    "Failed to fetch uploaded object metadata: " + ex.getMessage(), ex);
        }
        if (metadata.sizeBytes() != claim.fileSize()) {
            throw new BusinessException(AudioErrorCode.FILE_SIZE_MISMATCH,
                    "Uploaded bytes " + metadata.sizeBytes()
                            + " do not match expected " + claim.fileSize());
        }

        demoQuotaService.ensureWithinQuota(ownerId, metadata.sizeBytes());

        AudioFormat format = AudioFormat.fromContentType(claim.contentType());
        if (format == null) {
            throw new BusinessException(AudioErrorCode.UNSUPPORTED_AUDIO_FORMAT,
                    "Unsupported claimed content type: " + claim.contentType());
        }

        UUID demoId = UUID.randomUUID();
        Demo demo = new Demo(demoId, ownerId, request.title().trim(), request.s3Key(),
                metadata.sizeBytes(), request.voiceTagId());
        attachVoiceTagSnapshotIfPresent(demo, request.voiceTagId(), ownerId);
        demoRepository.save(demo);

        AudioProcessingJob job = new AudioProcessingJob(UUID.randomUUID(), demoId);
        jobRepository.save(job);

        uploadClaimService.delete(request.s3Key());

        audioProcessingEventPublisher.publish(new AudioProcessingEvent(
                demoId,
                request.s3Key(),
                request.voiceTagId(),
                request.watermarkInterval(),
                currentRequestId()));

        log.info("DEMO_UPLOAD_CONFIRMED demoId={} userId={} sizeBytes={} format={}",
                demoId, ownerId, metadata.sizeBytes(), format.extension());

        return new ConfirmUploadResponse(demoId, demo.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public DemoStatusResponse getStatus(UUID ownerId, UUID demoId) {
        Demo demo = demoRepository.findByIdAndOwnerId(demoId, ownerId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                        "Demo " + demoId + " not found for userId=" + ownerId));

        String playlistUrl = demo.getHlsPlaylistS3Key() == null
                ? null
                : objectStorageService.generatePublicUrl(StorageBucket.DEMO_AUDIO,
                        S3_KEY_STREAM_PREFIX + demo.getHlsPlaylistS3Key());

        return demoMapper.toStatusResponse(demo, playlistUrl);
    }

    @Override
    @Transactional
    public RotateKeyResponse rotateKey(UUID ownerId, UUID demoId) {
        Demo demo = demoRepository.findByIdAndOwnerId(demoId, ownerId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.DEMO_NOT_FOUND,
                        "Demo " + demoId + " not found for userId=" + ownerId));
        AesKeyRotationService.RotationResult result = aesKeyRotationService.rotateOnDemand(
                demo.getId(), ownerId);
        return new RotateKeyResponse(result.demoId(), result.newVersion(), result.rotated(),
                "AES_KEY_ROTATED");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DemoListItemResponse> list(UUID ownerId, Pageable pageable) {
        return demoRepository.findByOwnerId(ownerId, pageable)
                .map(demoMapper::toListItemResponse);
    }

    private void validateRequest(PresignedUrlRequest request) {
        String lower = request.fileName() == null ? "" : request.fileName().toLowerCase(Locale.ROOT);
        AudioFormat byName = AudioFormat.fromFileName(request.fileName());
        AudioFormat byType = AudioFormat.fromContentType(request.contentType());
        if (byName == null || byType == null || byName != byType) {
            throw new BusinessException(AudioErrorCode.UNSUPPORTED_AUDIO_FORMAT,
                    "File extension and content type do not match a supported audio format");
        }
    }

    private String currentRequestId() {
        return UUID.randomUUID().toString();
    }

    private void attachVoiceTagSnapshotIfPresent(Demo demo, UUID voiceTagId, UUID ownerId) {
        if (voiceTagId == null) {
            return;
        }
        VoiceTag tag = voiceTagRepository.findById(voiceTagId)
                .orElseThrow(() -> new BusinessException(AudioErrorCode.VOICE_TAG_FORBIDDEN,
                        "voiceTagId " + voiceTagId + " is not accessible"));
        if (!tag.getOwnerId().equals(ownerId)) {
            log.warn("VOICE_TAG_IDOR_ATTEMPT tagId={} actualOwnerId={} requesterUserId={}",
                    tag.getId(), tag.getOwnerId(), ownerId);
            throw new BusinessException(AudioErrorCode.VOICE_TAG_FORBIDDEN,
                    "voiceTagId " + voiceTagId + " is not accessible");
        }
        if (tag.isDeleted()) {
            throw new BusinessException(AudioErrorCode.VOICE_TAG_FORBIDDEN,
                    "voiceTagId " + voiceTagId + " has been deleted");
        }
        demo.attachVoiceTagSnapshot(tag.getOwnerId(), tag.getTextContent(),
                tag.getLanguageCode(), tag.getVoiceName());
    }
}