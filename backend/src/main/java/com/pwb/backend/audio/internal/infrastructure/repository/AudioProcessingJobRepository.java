package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.enums.JobStatus;
import com.pwb.backend.audio.internal.domain.model.AudioProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AudioProcessingJobRepository extends JpaRepository<AudioProcessingJob, String> {

  Optional<AudioProcessingJob> findByDemoId(String demoId);

  Optional<AudioProcessingJob> findByDemoIdAndDeletedFalse(String demoId);

  @Query("SELECT j FROM AudioProcessingJob j WHERE j.status IN :statuses AND j.updatedAt < :before AND j.deleted = false")
  List<AudioProcessingJob> findStuckJobsByStatuses(@Param("statuses") List<JobStatus> statuses, @Param("before") Instant before);
}
