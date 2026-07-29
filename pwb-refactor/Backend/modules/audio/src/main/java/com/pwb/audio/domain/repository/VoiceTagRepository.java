package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.audio.domain.model.VoiceTag;

import java.util.Optional;
import java.util.UUID;

public interface VoiceTagRepository {

    VoiceTag save(VoiceTag voiceTag);

    Optional<VoiceTag> findById(UUID id);

    Optional<VoiceTag> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    boolean existsByVoiceTagIdInConfig(UUID voiceTagId);
}
