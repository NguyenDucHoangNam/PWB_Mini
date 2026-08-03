package com.pwb.audio.infrastructure.persistence.repository;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SongJpaRepository extends AudioJpaRepository<SongJpaEntity> {

    @EntityGraph(attributePaths = {})
    Optional<SongJpaEntity> findByIdAndDeletedFalse(UUID id);

    @EntityGraph(attributePaths = {})
    Optional<SongJpaEntity> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SongJpaEntity s WHERE s.id = :id AND s.deleted = false")
    Optional<SongJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);

    Page<SongJpaEntity> findAllByUserIdAndDeletedFalse(UUID userId, Pageable pageable);
}
