package com.pwb.backend.shared.outbox.repository;

import com.pwb.backend.shared.outbox.enums.OutboxEventStatus;
import com.pwb.backend.shared.outbox.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Standard outbox access.
 *
 * <p>H4: there used to be two near-identical polling methods that drifted
 * apart in semantics (one native with row locks, one JPQL without). They
 * are kept as documented specialisations so existing callers compile, but
 * the production scheduler uses {@link #findPendingEventsForUpdate(int)}.
 *
 * <p>Production: {@link #findPendingEventsForUpdate(int)} —
 * native query, row-level {@code FOR UPDATE SKIP LOCKED}, narrow column
 * projection. Safe under horizontal scale.
 *
 * <p>Tests / single-instance dev: {@link #findReadyForProcessing} — JPQL,
 * no row lock, used when Debezium + Postgres is not available.
 */
@Repository
public interface OutboxEventRepository<T extends OutboxEvent> extends JpaRepository<T, String> {

    /**
     * Production-grade poll. Native so we can issue
     * {@code FOR UPDATE SKIP LOCKED} and select only the columns the
     * processor actually needs (avoids streaming the full {@code payload}
     * TEXT to callers that just want to dispatch).
     */
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

    /**
     * Test/dev convenience. JPQL with no row lock — only safe in
     * single-instance deployments where no scheduler races the CDC handler.
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.status = :status "
        + "AND e.deleted = false AND e.availableAt <= :now "
        + "ORDER BY e.createdAt ASC")
    List<T> findReadyForProcessing(@Param("status") OutboxEventStatus status,
                                   @Param("now") Instant now);
}