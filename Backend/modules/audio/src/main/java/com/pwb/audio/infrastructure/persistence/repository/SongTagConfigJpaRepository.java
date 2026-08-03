package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.infrastructure.persistence.entity.SongTagConfigJpaEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * A tag config lives and dies with its song, so it is hard-deleted and never filtered on {@code deleted}.
 */
public interface SongTagConfigJpaRepository extends AudioJpaRepository<SongTagConfigJpaEntity> {

    Optional<SongTagConfigJpaEntity> findBySongId(UUID songId);

    boolean existsByVoiceTagId(UUID voiceTagId);

    void deleteBySongId(UUID songId);
}
