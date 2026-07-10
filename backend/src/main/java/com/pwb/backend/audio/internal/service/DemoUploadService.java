package com.pwb.backend.audio.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.audio.internal.api.ConfirmUploadRequest;
import com.pwb.backend.audio.internal.api.ConfirmUploadResponse;
import com.pwb.backend.audio.internal.api.PresignedUrlRequest;
import com.pwb.backend.audio.internal.api.PresignedUrlResponse;
import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.enums.DemoStatus;
import com.pwb.backend.audio.internal.enums.JobStatus;
import com.pwb.backend.audio.internal.event.AudioProcessingEvent;
import com.pwb.backend.audio.internal.helper.MimeToExtensionMapper;
import com.pwb.backend.audio.internal.helper.S3KeyBuilder;
import com.pwb.backend.audio.internal.helper.UploadClaim;
import com.pwb.backend.audio.internal.helper.UploadClaimService;
import com.pwb.backend.audio.internal.model.AudioProcessingJob;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.repository.AudioProcessingJobRepository;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoUploadService {

  private final AudioProperties audioProperties;
  private final MimeToExtensionMapper mimeToExtensionMapper;
  private final S3KeyBuilder s3KeyBuilder;
  private final UploadClaimService uploadClaimService;
  private final StorageService storageService;
  private final QuotaService quotaService;
  private final DemoRepository demoRepository;
  private final AudioProcessingJobRepository jobRepository;
  private final KafkaTemplate<String, Object> audioProcessingKafkaTemplate;
  private final ObjectMapper objectMapper;

  public PresignedUrlResponse generatePresignedUrl(String userId, PresignedUrlRequest req) {
    long fileSize = req.fileSize();
    String extension = mimeToExtensionMapper.toExtension(req.contentType());
    String s3Key = s3KeyBuilder.generateOriginalKey(userId, extension);

    uploadClaimService.put(s3Key, userId, fileSize, req.contentType());

    int ttlSeconds = audioProperties.getUpload().getPresignedUrlTtl();
    String uploadUrl = storageService.generatePresignedUploadUrl(
        s3Key, req.contentType(), fileSize, Math.max(1, ttlSeconds / 60));

    return new PresignedUrlResponse(
        uploadUrl,
        s3Key,
        ttlSeconds,
        Instant.now(),
        audioProperties.getUpload().getMaxFileSize()
    );
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public ConfirmUploadResponse confirmUpload(String userId, ConfirmUploadRequest req) {
    String s3Key = req.s3Key();

    UploadClaim claim = uploadClaimService.get(s3Key)
        .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_CLAIM_EXPIRED,
            "Upload claim is missing. Re-request presigned upload URL first."));
    if (!claim.userId().equals(userId)) {
      throw new BusinessException(ErrorCode.INVALID_S3_KEY_OWNER,
          "You do not own this S3 key");
    }

    boolean sizeOk = storageService.verifyFile(s3Key, claim.expectedSizeBytes());
    if (!sizeOk) {
      log.warn("File size mismatch, deleting rogue upload");
      try {
        storageService.deleteFile(s3Key);
      } catch (Exception ex) {
        log.warn("Failed to delete rogue upload", ex);
      }
      throw new BusinessException(ErrorCode.FILE_SIZE_MISMATCH,
          "Uploaded file size does not match expected size");
    }
    long actualSize = claim.expectedSizeBytes();

    quotaService.validateUploadQuota(userId, actualSize);

    String extension = mimeToExtensionMapper.toExtension(claim.expectedContentType());
    String confirmedKey = s3KeyBuilder.generateConfirmedKey(
        userId,
        extractUuidFromOriginalKey(s3Key),
        extension
    );

    Demo demo = new Demo();
    demo.setOwnerId(userId);
    demo.setTitle(req.title().trim());
    demo.setOriginalS3Key(s3Key);
    demo.setConfirmedS3Key(confirmedKey);
    demo.setFileSize(actualSize);
    demo.setVoiceTagId(req.voiceTagId());
    demo.setWatermarkInterval(req.watermarkInterval() == null ? 25 : req.watermarkInterval());
    demo.setStatus(DemoStatus.PROCESSING);

    Demo saved = demoRepository.save(demo);

    AudioProcessingJob job = new AudioProcessingJob();
    job.setDemoId(saved.getId());
    job.setStatus(JobStatus.PENDING);
    job.setAttemptCount(0);
    job = jobRepository.save(job);

    try {
      storageService.setObjectTags(s3Key, Map.of("confirmed", "true"));
      storageService.copyObject(s3Key, confirmedKey);
      storageService.deleteFile(s3Key);
      log.info("Moved original -> confirmed key for demo={}", saved.getId());
    } catch (Exception ex) {
      log.error("S3 copy/delete failed for demo={}", saved.getId(), ex);
      demoRepository.delete(saved);
      jobRepository.delete(job);
      throw new BusinessException(ErrorCode.FILE_NOT_FOUND_ON_S3,
          "Failed to relocate uploaded file");
    }

    uploadClaimService.delete(s3Key);

    AudioProcessingEvent event = new AudioProcessingEvent(
        saved.getId(),
        confirmedKey,
        userId,
        saved.getWatermarkInterval(),
        saved.getVoiceTagId()
    );
    publishEvent(saved.getId(), event);

    return new ConfirmUploadResponse(saved.getId(), saved.getStatus().name());
  }

  private void publishEvent(String demoId, AudioProcessingEvent event) {
    try {
      String json = objectMapper.writeValueAsString(event);
      audioProcessingKafkaTemplate.send("audio-processing-events", demoId, json);
      log.info("Published audio-processing-events for demoId={}", demoId);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize AudioProcessingEvent for demoId={}", demoId, ex);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to publish processing event");
    }
  }

  private String extractUuidFromOriginalKey(String s3Key) {
    int lastSlash = s3Key.lastIndexOf('/');
    int dot = s3Key.lastIndexOf('.');
    if (lastSlash < 0 || dot <= lastSlash) {
      return java.util.UUID.randomUUID().toString();
    }
    String uuid = s3Key.substring(lastSlash + 1, dot);
    if (!uuid.matches("[A-Za-z0-9-]{1,128}")) {
      return java.util.UUID.randomUUID().toString();
    }
    return uuid;
  }
}
