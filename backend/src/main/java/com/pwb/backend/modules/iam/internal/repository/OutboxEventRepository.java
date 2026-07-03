package com.pwb.backend.modules.iam.internal.repository;

import com.pwb.backend.modules.iam.internal.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

  @Query(value = "SELECT * FROM outbox_events "
      + "WHERE status = 'PENDING' AND deleted = false "
      + "ORDER BY created_at ASC "
      + "LIMIT :limit "
      + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> findPendingEventsForUpdate(@Param("limit") int limit);
}
