package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.DemoDistribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DemoDistributionRepository extends JpaRepository<DemoDistribution, String> {

  @Query("SELECT d FROM DemoDistribution d WHERE d.shareToken = :token AND d.deleted = false")
  Optional<DemoDistribution> findByShareToken(@Param("token") UUID token);

  Optional<DemoDistribution> findByIdAndDeletedFalse(String id);

  @Query("SELECT d FROM DemoDistribution d WHERE d.demoId = :demoId AND d.isRevoked = false "
      + "AND d.deleted = false")
  Page<DemoDistribution> findActiveByDemo(@Param("demoId") String demoId, Pageable pageable);

  @Query("SELECT d FROM DemoDistribution d WHERE d.demoId = :demoId AND d.deleted = false")
  Page<DemoDistribution> findAllByDemo(@Param("demoId") String demoId, Pageable pageable);

  @Query("SELECT d FROM DemoDistribution d WHERE d.threadId = :threadId AND d.deleted = false "
      + "ORDER BY d.createdAt DESC")
  List<DemoDistribution> findByThread(@Param("threadId") String threadId);

  @Query("SELECT COUNT(d) FROM DemoDistribution d WHERE d.demoId = :demoId AND d.deleted = false")
  long countByDemoId(@Param("demoId") String demoId);

  @Query("SELECT d FROM DemoDistribution d WHERE d.demoId = :demoId AND d.isRevoked = false "
      + "AND d.deleted = false")
  List<DemoDistribution> findActiveByDemoId(@Param("demoId") String demoId);
}
