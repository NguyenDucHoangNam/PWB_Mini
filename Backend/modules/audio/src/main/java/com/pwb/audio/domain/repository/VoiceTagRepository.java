package com.pwb.audio.domain.repository;

import com.pwb.audio.domain.model.VoiceTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface VoiceTagRepository {

    VoiceTag save(VoiceTag voiceTag);

    Optional<VoiceTag> findById(UUID id);

    Optional<VoiceTag> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id);

    Page<VoiceTag> findAllByUserId(UUID userId, Pageable pageable);

    /** Hard delete: voice tags carry no soft-delete state, removal is permanent. */
    void deleteById(UUID id);
}
