package com.pwb.backend.audio.internal.repository;

import com.pwb.backend.audio.internal.model.DemoDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface DemoDownloadRepository extends JpaRepository<DemoDownload, String> {

  @Modifying
  @Query("DELETE FROM DemoDownload d WHERE d.downloadedAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}