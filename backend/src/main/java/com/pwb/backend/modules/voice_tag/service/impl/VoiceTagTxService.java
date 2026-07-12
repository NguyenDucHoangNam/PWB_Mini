package com.pwb.backend.modules.voice_tag.service.impl;

import com.pwb.backend.modules.audio.enums.DemoStatus;
import com.pwb.backend.modules.audio.repository.DemoRepository;
import com.pwb.backend.modules.voice_tag.config.VoiceTagProperties;
import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagTxService {

    private final VoiceTagRepository voiceTagRepository;
    private final DemoRepository demoRepository;
    private final VoiceTagProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistNew(VoiceTag tag) {
        voiceTagRepository.save(tag);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int clearDefaultForOwner(UUID ownerId) {
        return voiceTagRepository.clearDefaultForOwner(ownerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public void setDefaultAtomic(UUID tagId, UUID ownerId) {
        voiceTagRepository.clearDefaultForOwner(ownerId);
        VoiceTag tag = voiceTagRepository.findActiveById(tagId)
                .orElseThrow();
        tag.setDefault(true);
        voiceTagRepository.save(tag);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSoftDeleted(UUID tagId) {
        VoiceTag tag = voiceTagRepository.findActiveById(tagId)
                .orElseThrow();
        tag.setDefault(false);
        tag.setDeleted(true);
        tag.softDelete(tag.getOwnerId().toString());
        voiceTagRepository.save(tag);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRestored(UUID tagId) {
        VoiceTag tag = voiceTagRepository.findByIdIncludingDeleted(tagId)
                .orElseThrow();
        tag.setDeleted(false);
        tag.restore();
        voiceTagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public long countActiveDemosReferencing(UUID tagId) {
        return demoRepository.countByVoiceTagIdAndStatus(tagId, DemoStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean isWithinRestoreWindow(VoiceTag tag) {
        if (tag.getDeletedAt() == null) {
            return true;
        }
        Duration elapsed = Duration.between(tag.getDeletedAt(), Instant.now());
        return elapsed.toDays() < properties.getRestoreWindowDays();
    }
}
