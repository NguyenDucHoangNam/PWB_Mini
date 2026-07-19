package com.pwb.voice.infrastructure.persistence.repository;

import com.pwb.voice.api.enums.SongStatus;
import com.pwb.voice.infrastructure.persistence.entity.SongJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SongJpaRepository
        extends JpaRepository<SongJpaEntity, UUID>, JpaSpecificationExecutor<SongJpaEntity> {

    Optional<SongJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    Page<SongJpaEntity> findByUserIdAndDeletedFalse(UUID userId, Pageable pageable);

    Page<SongJpaEntity> findByUserIdAndStatusAndDeletedFalse(UUID userId, SongStatus status, Pageable pageable);

    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
}
