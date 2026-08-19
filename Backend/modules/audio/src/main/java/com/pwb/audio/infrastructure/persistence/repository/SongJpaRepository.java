package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Songs are hard-deleted, so none of these queries filter on the inherited {@code deleted} flag.
 */
public interface SongJpaRepository extends AudioJpaRepository<SongJpaEntity> {

    Optional<SongJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<SongJpaEntity> findAllByUserId(UUID userId, Pageable pageable);

    Page<SongJpaEntity> findAllByUserIdAndStatusIn(UUID userId, Collection<SongStatus> statuses, Pageable pageable);

    boolean existsByOriginalS3Key(String originalS3Key);
}
