package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.api.dto.request.CreateVoiceTagRequest;
import com.pwb.backend.audio.api.dto.response.CreateVoiceTagResponse;
import com.pwb.backend.audio.api.dto.response.VoiceTagListResponse;
import com.pwb.backend.audio.api.dto.response.VoiceTagPreviewResponse;
import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.domain.enums.DemoStatus;
import com.pwb.backend.audio.internal.application.helper.GcpTtsClient;
import com.pwb.backend.audio.internal.application.helper.SsmlSanitizer;
import com.pwb.backend.audio.internal.application.helper.VoiceTagIdorDetector;
import com.pwb.backend.audio.internal.domain.model.VoiceTag;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoRepository;
import com.pwb.backend.audio.internal.infrastructure.repository.VoiceTagRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.audio.internal.domain.exception.AudioErrorCode;
import com.pwb.backend.shared.web.security.ClientIpResolver;
import com.pwb.backend.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagService {

  private static final String VOICE_TAG_S3_PREFIX = "voicetags/";
  private static final String VOICE_TAG_S3_SUFFIX = ".mp3";

  private final VoiceTagRepository voiceTagRepository;
  private final DemoRepository demoRepository;
  private final StorageService storageService;
  private final GcpTtsClient gcpTtsClient;
  private final SsmlSanitizer ssmlSanitizer;
  private final VoiceTagQuotaService quotaService;
  private final VoiceTagIdorDetector idorDetector;
  private final ClientIpResolver clientIpResolver;
  private final AudioProperties audioProperties;

  @Transactional
  public CreateVoiceTagResponse createVoiceTag(String userId, CreateVoiceTagRequest request) {
    String ip = clientIpResolver.current();
    if (idorDetector.isBlocked(ip)) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Too many unauthorized access attempts");
    }
    quotaService.trackDailyCreate(userId);
    quotaService.reserveActiveSlot(userId);

    SsmlSanitizer.SanitizedSsml sanitized = ssmlSanitizer.sanitize(request.textContent());
    if (!gcpTtsClient.isVoiceAllowed(request.voiceName())) {
      quotaService.releaseActiveSlot(userId);
      throw new BusinessException(AudioErrorCode.INVALID_VOICE_NAME,
          "Voice name is not allowed for this account");
    }

    byte[] audioBytes;
    try {
      audioBytes = gcpTtsClient.synthesize(
          sanitized.sanitized(), request.languageCode(), request.voiceName());
    } catch (RuntimeException ex) {
      quotaService.releaseActiveSlot(userId);
      throw ex;
    }

    int min = audioProperties.getTts().getResponseMinBytes();
    int max = audioProperties.getTts().getResponseMaxBytes();
    if (audioBytes.length < min || audioBytes.length > max) {
      quotaService.releaseActiveSlot(userId);
      log.warn("GCP_TTS_INVALID_BYTES bytesReceived={} voiceName={}",
          audioBytes.length, request.voiceName());
      throw new BusinessException(AudioErrorCode.TTS_SERVICE_FAILED_INVALID,
          "TTS response bytes failed size validation");
    }
    if (!gcpTtsClient.looksLikeMp3(audioBytes)) {
      quotaService.releaseActiveSlot(userId);
      throw new BusinessException(AudioErrorCode.TTS_SERVICE_FAILED_INVALID,
          "TTS response magic number is not MP3");
    }

    String s3Key = VOICE_TAG_S3_PREFIX + UUID.randomUUID() + VOICE_TAG_S3_SUFFIX;
    try {
      quotaService.trackStorage(userId, audioBytes.length);
      storageService.uploadFile(s3Key,
          new ByteArrayInputStream(audioBytes),
          audioBytes.length,
          "audio/mpeg");

      boolean willBeDefault = voiceTagRepository.countActiveByOwner(userId) == 0;

      VoiceTag entity = new VoiceTag();
      entity.setOwnerId(userId);
      entity.setTextContent(sanitized.sanitized());
      entity.setVoiceName(request.voiceName());
      entity.setLanguageCode(request.languageCode());
      entity.setS3Key(s3Key);
      entity.setFileSize(audioBytes.length);
      entity.setDefault(willBeDefault);
      entity.setDeleted(false);
      VoiceTag saved = voiceTagRepository.save(entity);
      log.info("VOICE_TAG_CREATED id={} userId={} bytes={}",
          saved.getId(), userId, audioBytes.length);
      return new CreateVoiceTagResponse(
          saved.getId(),
          saved.getTextContent(),
          saved.getLanguageCode(),
          saved.getVoiceName(),
          saved.isDefault(),
          saved.getCreatedAt()
      );
    } catch (RuntimeException ex) {
      log.error("VOICE_TAG_CREATE_FAILED userId={} s3Key={}", userId, s3Key, ex);
      try {
        storageService.deleteFile(s3Key);
      } catch (RuntimeException cleanupEx) {
        log.error("S3_COMPENSATION_ORPHAN s3Key={} cause={}", s3Key, cleanupEx.getClass().getSimpleName());
      }
      quotaService.releaseActiveSlot(userId);
      quotaService.releaseStorage(userId, audioBytes.length);
      throw ex;
    }
  }

  @Transactional
  public VoiceTagPreviewResponse previewVoiceTag(String userId, String tagId) {
    VoiceTag tag = voiceTagRepository.findActiveById(tagId)
        .orElseThrow(() -> new BusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND, "Voice tag not found"));
    if (!tag.getOwnerId().equals(userId)) {
      idorDetector.record(tagId, userId);
      throw new BusinessException(ErrorCode.FORBIDDEN, "You are not the owner of this voice tag");
    }
    int ttlSeconds = audioProperties.getVoiceTag().getPresignedTtlSeconds();
    int minutes = Math.max(1, ttlSeconds / 60);
    String url = storageService.generatePresignedDownloadUrl(tag.getS3Key(), minutes);
    return new VoiceTagPreviewResponse(url, ttlSeconds);
  }

  @Transactional
  public void setDefaultVoiceTag(String userId, String tagId) {
    VoiceTag tag = voiceTagRepository.findActiveByIdAndOwner(tagId, userId)
        .orElseThrow(() -> new BusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND, "Voice tag not found"));
    if (tag.isDefault()) {
      return;
    }
    voiceTagRepository.clearDefaultForOwner(userId);
    tag.setDefault(true);
    voiceTagRepository.save(tag);
  }

  @Transactional(readOnly = true)
  public VoiceTagListResponse listVoiceTags(String userId) {
    List<VoiceTag> tags = voiceTagRepository.findActiveByOwner(userId);
    List<VoiceTagListResponse.Item> items = tags.stream()
        .map(t -> new VoiceTagListResponse.Item(
            t.getId(),
            t.getTextContent(),
            t.getLanguageCode(),
            t.getVoiceName(),
            t.isDefault(),
            t.getCreatedAt()
        ))
        .toList();
    return new VoiceTagListResponse(items);
  }

  @Transactional
  public void softDeleteVoiceTag(String userId, String tagId) {
    VoiceTag tag = voiceTagRepository.findActiveByIdAndOwner(tagId, userId)
        .orElseThrow(() -> new BusinessException(AudioErrorCode.VOICE_TAG_NOT_FOUND, "Voice tag not found"));
    long activeDemoCount = demoRepository.countByVoiceTagIdAndStatusAndDeletedFalse(
        tagId, DemoStatus.ACTIVE);
    if (activeDemoCount > 0) {
      throw new BusinessException(AudioErrorCode.VOICE_TAG_IN_USE,
          "Voice tag is referenced by " + activeDemoCount + " active demos");
    }

    try {
      storageService.deleteFile(tag.getS3Key());
    } catch (RuntimeException ex) {
      log.error("VOICE_TAG_S3_CLEANUP_RETRY tagId={} attempt=1 s3Key={}",
          tagId, tag.getS3Key(), ex);
    }

    tag.setDeleted(true);
    tag.setDeletedAt(Instant.now());
    voiceTagRepository.save(tag);
    if (tag.isDefault()) {
      voiceTagRepository.clearDefaultForOwner(userId);
    }
    quotaService.releaseActiveSlot(userId);
    quotaService.releaseStorage(userId, tag.getFileSize());
    log.info("VOICE_TAG_S3_DELETED tagId={} s3Key={}", tagId, tag.getS3Key());
  }
}
