package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface SongTagConfigJpaRepository extends AudioJpaRepository<SongTagConfigJpaEntity> {

    @EntityGraph(attributePaths = {})
    Optional<SongTagConfigJpaEntity> findBySongIdAndDeletedFalse(UUID songId);

    boolean existsBySongIdAndDeletedFalse(UUID songId);
}
