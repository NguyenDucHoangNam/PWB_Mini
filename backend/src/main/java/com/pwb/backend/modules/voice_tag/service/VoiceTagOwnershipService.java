package com.pwb.backend.modules.voice_tag.service;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.modules.voice_tag.entity.VoiceTag;
import com.pwb.backend.modules.voice_tag.exception.VoiceTagErrorCode;
import com.pwb.backend.modules.voice_tag.repository.VoiceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTagOwnershipService {

    private final VoiceTagRepository voiceTagRepository;

    public VoiceTag loadActiveOrThrow(UUID tagId, UUID currentUserId) {
        VoiceTag tag = voiceTagRepository.findActiveById(tagId)
                .orElseThrow(() -> new BusinessException(VoiceTagErrorCode.VOICE_TAG_NOT_FOUND));
        checkOwner(tag, currentUserId);
        return tag;
    }

    public VoiceTag loadIncludingDeletedOrThrow(UUID tagId, UUID currentUserId) {
        VoiceTag tag = voiceTagRepository.findByIdIncludingDeleted(tagId)
                .orElseThrow(() -> new BusinessException(VoiceTagErrorCode.VOICE_TAG_NOT_FOUND));
        checkOwner(tag, currentUserId);
        return tag;
    }

    private void checkOwner(VoiceTag tag, UUID currentUserId) {
        if (!tag.getOwnerId().equals(currentUserId)) {
            log.warn("IDOR_ATTEMPT tagId={} requesterUserId={}",
                    tag.getId(), currentUserId);
            throw new BusinessException(VoiceTagErrorCode.FORBIDDEN_ACCESS);
        }
    }
}
