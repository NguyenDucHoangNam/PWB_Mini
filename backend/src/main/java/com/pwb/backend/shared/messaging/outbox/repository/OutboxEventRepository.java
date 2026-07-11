package com.pwb.backend.shared.messaging.outbox.repository;

import com.pwb.backend.shared.messaging.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository<T extends OutboxEvent> extends JpaRepository<T, String> {

    /**
     * Returns a batch of pending events for the scheduler. The query orders by
     * {@code (created_at, id)} so two rows with the same millisecond timestamp still have
     * a deterministic ordering and cannot be skipped between polls.
     */
    @Query(value = """
        SELECT id, aggregate_type, aggregate_id, event_type, idempotency_key,
               status, retry_count, available_at
        FROM outbox_events
        WHERE status = 'PENDING' AND deleted = false
        AND available_at <= NOW()
        ORDER BY created_at ASC, id ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<T> findPendingEventsForUpdate(@Param("limit") int limit);

    @Query(value = """
        SELECT id
        FROM outbox_events
        WHERE status = 'IN_FLIGHT' AND deleted = false
        AND processing_started_at <= :staleBefore
        ORDER BY processing_started_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<String> findStaleInFlightIds(@Param("staleBefore") Instant staleBefore,
                                      @Param("limit") int limit);

    Optional<T> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT e FROM #{#entityName} e WHERE e.status = :status "
        + "AND e.deleted = false AND e.availableAt <= :now "
        + "ORDER BY e.createdAt ASC, e.id ASC")
    List<T> findReadyForProcessing(@Param("status") OutboxEventStatus status,
                                   @Param("now") Instant now);
}