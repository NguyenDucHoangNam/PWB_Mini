package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Voice tags are hard-deleted, so none of these queries filter on the inherited {@code deleted} flag.
 */
public interface VoiceTagJpaRepository extends AudioJpaRepository<VoiceTagJpaEntity> {

    Optional<VoiceTagJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id);

    Page<VoiceTagJpaEntity> findAllByUserId(UUID userId, Pageable pageable);
}
