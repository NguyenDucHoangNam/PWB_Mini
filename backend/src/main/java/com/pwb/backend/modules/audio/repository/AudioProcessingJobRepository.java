package com.pwb.backend.modules.audio.repository;

import com.pwb.backend.modules.audio.entity.AudioProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AudioProcessingJobRepository extends JpaRepository<AudioProcessingJob, UUID> {

    Optional<AudioProcessingJob> findByDemoId(UUID demoId);
}