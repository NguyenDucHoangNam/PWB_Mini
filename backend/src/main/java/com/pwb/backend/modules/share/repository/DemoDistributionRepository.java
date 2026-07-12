package com.pwb.backend.modules.share.repository;

import com.pwb.backend.modules.share.entity.DemoDistribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DemoDistributionRepository extends JpaRepository<DemoDistribution, UUID> {

    Optional<DemoDistribution> findByShareToken(UUID shareToken);

    Page<DemoDistribution> findByDemoIdAndRevokedFalse(UUID demoId, Pageable pageable);

    Page<DemoDistribution> findByDemoId(UUID demoId, Pageable pageable);

    @Modifying
    @Query("UPDATE DemoDistribution d SET d.playCount = d.playCount + 1, d.lastPlayedAt = :now WHERE d.id = :id")
    int incrementPlayCount(@Param("id") UUID id, @Param("now") Instant now);
}