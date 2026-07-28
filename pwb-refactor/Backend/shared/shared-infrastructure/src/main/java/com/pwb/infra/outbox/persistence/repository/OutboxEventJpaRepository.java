package com.pwb.infra.outbox.persistence.repository;

import com.pwb.infra.outbox.core.OutboxStatus;
import com.pwb.infra.outbox.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            UPDATE outbox_events
            SET status = 'PROCESSING', next_attempt_at = :nextAttempt
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE status = 'PENDING' AND next_attempt_at <= :now
                ORDER BY created_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int claimBatch(@Param("batch") int batch,
                   @Param("now") Instant now,
                   @Param("nextAttempt") Instant nextAttempt);

    List<OutboxEventJpaEntity> findByStatus(OutboxStatus status);
}