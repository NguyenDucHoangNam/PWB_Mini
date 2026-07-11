package com.pwb.backend.audio.internal.application.service;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import com.pwb.backend.audio.internal.domain.enums.DemoStatus;
import com.pwb.backend.audio.internal.infrastructure.repository.DemoRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

  private final DemoRepository demoRepository;
  private final AudioProperties audioProperties;

  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public void validateUploadQuota(String ownerId, long newFileSize) {
    int activeQuota = audioProperties.getUpload().getPerUserActiveQuota();
    long storageQuota = audioProperties.getUpload().getPerUserStorageQuota();

    long activeCount = demoRepository.countByOwnerIdAndStatusAndDeletedFalse(ownerId, DemoStatus.ACTIVE);
    if (activeCount >= activeQuota) {
      log.warn("Active demo quota exceeded for owner={}: count={}, limit={}",
          ownerId, activeCount, activeQuota);
      throw new BusinessException(ErrorCode.DEMO_QUOTA_EXCEEDED,
          "Active demo quota exceeded: " + activeCount + "/" + activeQuota);
    }

    long currentStorage = demoRepository.sumFileSizeByOwnerIdAndStatus(ownerId, DemoStatus.ACTIVE);
    if (currentStorage + newFileSize > storageQuota) {
      log.warn("Storage quota exceeded for owner={}: current={}, new={}, limit={}",
          ownerId, currentStorage, newFileSize, storageQuota);
      throw new BusinessException(ErrorCode.AUDIO_QUOTA_EXCEEDED,
          "Storage quota exceeded: " + (currentStorage + newFileSize) + " > " + storageQuota);
    }
  }

  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public long countActiveDemos(String ownerId) {
    return demoRepository.countByOwnerIdAndStatusAndDeletedFalse(ownerId, DemoStatus.ACTIVE);
  }

  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public long sumActiveStorageBytes(String ownerId) {
    return demoRepository.sumFileSizeByOwnerIdAndStatus(ownerId, DemoStatus.ACTIVE);
  }
}
