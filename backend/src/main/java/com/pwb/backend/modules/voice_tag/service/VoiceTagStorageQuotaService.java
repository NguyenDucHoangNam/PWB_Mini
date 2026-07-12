package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagStorageQuotaService {

    private final VoiceTagRepository voiceTagRepository;
    private final VoiceTagProperties properties;

    public void checkCapacity(UUID ownerId, int estimatedBytes) {
        long projected = computeProjectedBytes(ownerId) + estimatedBytes;
        if (projected > properties.getMaxStorageBytesPerUser()) {
            log.warn("VOICE_TAG_STORAGE_EXCEEDED userId={} projected={} max={}",
                    ownerId, projected, properties.getMaxStorageBytesPerUser());
            throw new BusinessException(VoiceTagErrorCode.VOICE_TAG_STORAGE_EXCEEDED);
        }
    }

    public long computeProjectedBytes(UUID ownerId) {
        List<VoiceTag> tags = voiceTagRepository.findAllActiveByOwner(ownerId);
        long total = 0L;
        for (VoiceTag tag : tags) {
            total += tag.getFileSize();
        }
        return total;
    }

    public long maxStorageBytes() {
        return properties.getMaxStorageBytesPerUser();
    }
}
