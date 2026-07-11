package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.SharedThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedThreadRepository extends JpaRepository<SharedThread, String> {

  @Query("SELECT st FROM SharedThread st WHERE st.producerId = :producerId "
      + "AND st.recipientEmailHash = :hash AND st.deleted = false")
  Optional<SharedThread> findByProducerAndHash(@Param("producerId") String producerId,
                                                @Param("hash") String hash);

  @Query("SELECT st FROM SharedThread st WHERE st.producerId = :producerId "
      + "AND st.deleted = false ORDER BY st.lastInteractedAt DESC")
  List<SharedThread> findRecentByProducer(@Param("producerId") String producerId);
}
