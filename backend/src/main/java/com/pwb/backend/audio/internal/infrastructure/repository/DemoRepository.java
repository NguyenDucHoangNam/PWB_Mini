package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.enums.DemoStatus;
import com.pwb.backend.audio.internal.domain.model.Demo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DemoRepository extends JpaRepository<Demo, String> {

  Optional<Demo> findByIdAndDeletedFalse(String id);

  Optional<Demo> findByIdAndOwnerIdAndDeletedFalse(String id, String ownerId);

  long countByOwnerIdAndStatusAndDeletedFalse(String ownerId, DemoStatus status);

  @Query("SELECT COALESCE(SUM(d.fileSize), 0L) FROM Demo d WHERE d.ownerId = :ownerId AND d.status = :status AND d.deleted = false")
  long sumFileSizeByOwnerIdAndStatus(@Param("ownerId") String ownerId, @Param("status") DemoStatus status);

  @Query("SELECT d FROM Demo d WHERE d.status = :status AND d.updatedAt < :before AND d.deleted = false")
  List<Demo> findStuckByStatus(@Param("status") DemoStatus status, @Param("before") Instant before);

  boolean existsByOriginalS3Key(String originalS3Key);

  Optional<Demo> findByConfirmedS3Key(String confirmedS3Key);

  long countByVoiceTagIdAndStatusAndDeletedFalse(String voiceTagId, DemoStatus status);
}
