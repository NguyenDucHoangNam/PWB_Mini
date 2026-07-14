package com.pwb.backend.modules.audio.repository;

import com.pwb.backend.modules.audio.entity.Demo;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DemoRepository extends JpaRepository<Demo, UUID> {

    Optional<Demo> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Demo> findByOriginalS3Key(String originalS3Key);

    Page<Demo> findByOwnerId(UUID ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(d) FROM Demo d WHERE d.ownerId = :ownerId AND d.status = :status")
    long countByOwnerIdAndStatusForUpdate(@Param("ownerId") UUID ownerId, @Param("status") DemoStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(SUM(d.fileSize), 0L) FROM Demo d WHERE d.ownerId = :ownerId AND d.status = :status")
    long sumFileSizeByOwnerIdAndStatusForUpdate(@Param("ownerId") UUID ownerId, @Param("status") DemoStatus status);
    @Query("""
            SELECT COUNT(d) FROM Demo d WHERE d.voiceTagId = :voiceTagId AND d.status = :status
            """)
    long countByVoiceTagIdAndStatus(@Param("voiceTagId") UUID voiceTagId, @Param("status") DemoStatus status);
}