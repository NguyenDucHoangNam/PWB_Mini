package com.pwb.backend.shared.outbox.repository;

import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository<T extends OutboxEvent> extends JpaRepository<T, String> {

    @Query(value = """
        SELECT id, aggregate_type, aggregate_id, event_type, idempotency_key,
               status, retry_count, available_at
        FROM outbox_events
        WHERE status = 'PENDING' AND deleted = false
        AND available_at <= NOW()
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<T> findPendingEventsForUpdate(@Param("limit") int limit);

    @Query("SELECT e FROM #{#entityName} e WHERE e.status = :status "
        + "AND e.deleted = false AND e.availableAt <= :now "
        + "ORDER BY e.createdAt ASC")
    List<T> findReadyForProcessing(@Param("status") OutboxEventStatus status,
                                   @Param("now") Instant now);
}