package com.pwb.voice.infrastructure.persistence.repository;

import com.pwb.voice.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SongTagConfigJpaRepository
        extends JpaRepository<SongTagConfigJpaEntity, UUID>, JpaSpecificationExecutor<SongTagConfigJpaEntity> {

    Optional<SongTagConfigJpaEntity> findBySongIdAndDeletedFalse(UUID songId);

    boolean existsBySongIdAndDeletedFalse(UUID songId);
}
