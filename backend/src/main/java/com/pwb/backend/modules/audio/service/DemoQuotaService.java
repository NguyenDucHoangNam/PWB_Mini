package com.pwb.backend.modules.audio.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.audio.config.AudioProperties;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.exception.AudioErrorCode;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoQuotaService {

    private final DemoRepository demoRepository;
    private final AudioProperties audioProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void ensureWithinQuota(UUID ownerId, long additionalBytes) {
        long activeCount = demoRepository.countByOwnerIdAndStatusForUpdate(ownerId, DemoStatus.ACTIVE);
        long activeBytes = demoRepository.sumFileSizeByOwnerIdAndStatusForUpdate(ownerId, DemoStatus.ACTIVE);

        if (activeCount >= audioProperties.getQuota().getMaxActiveDemosPerUser()) {
            log.warn("DEMO_QUOTA_EXCEEDED userId={} active={} max={}",
                    ownerId, activeCount, audioProperties.getQuota().getMaxActiveDemosPerUser());
            throw new BusinessException(AudioErrorCode.DEMO_QUOTA_EXCEEDED,
                    "Active demos " + activeCount + " reached limit "
                            + audioProperties.getQuota().getMaxActiveDemosPerUser());
        }

        long projectedBytes = activeBytes + additionalBytes;
        if (projectedBytes > audioProperties.getQuota().getMaxActiveAudioBytesPerUser()) {
            log.warn("AUDIO_QUOTA_EXCEEDED userId={} activeBytes={} additional={} max={}",
                    ownerId, activeBytes, additionalBytes,
                    audioProperties.getQuota().getMaxActiveAudioBytesPerUser());
            throw new BusinessException(AudioErrorCode.AUDIO_QUOTA_EXCEEDED,
                    "Active bytes " + activeBytes + " + " + additionalBytes
                            + " exceeds quota " + audioProperties.getQuota().getMaxActiveAudioBytesPerUser());
        }
    }
}