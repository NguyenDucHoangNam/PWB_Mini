package com.pwb.infra.outbox.persistence.repository;

import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            WITH claimed AS (
                SELECT id
                FROM outbox_events
                WHERE status = 'PENDING'
                  AND next_attempt_at <= :now
                ORDER BY created_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events o
            SET status = 'PROCESSING',
                next_attempt_at = :nextAttempt,
                lease_until = :leaseUntil
            FROM claimed
            WHERE o.id = claimed.id
            RETURNING o.id
            """, nativeQuery = true)
    List<UUID> claimBatch(@Param("batch") int batch,
                          @Param("now") Instant now,
                          @Param("nextAttempt") Instant nextAttempt,
                          @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Query(value = """
            WITH reclaimed AS (
                SELECT id
                FROM outbox_events
                WHERE status = 'PROCESSING'
                  AND lease_until IS NOT NULL
                  AND lease_until <= :now
                ORDER BY created_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events o
            SET next_attempt_at = :nextAttempt,
                lease_until = :leaseUntil
            FROM reclaimed
            WHERE o.id = reclaimed.id
            RETURNING o.id
            """, nativeQuery = true)
    List<UUID> reclaimExpiredLease(@Param("batch") int batch,
                                    @Param("now") Instant now,
                                    @Param("nextAttempt") Instant nextAttempt,
                                    @Param("leaseUntil") Instant leaseUntil);

    List<OutboxEventJpaEntity> findAllByIdInAndStatusOrderByCreatedAtAsc(
            Collection<UUID> ids, OutboxStatus status);
}