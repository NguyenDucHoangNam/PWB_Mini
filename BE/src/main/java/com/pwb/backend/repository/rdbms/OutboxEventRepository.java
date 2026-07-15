package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.OutboxEvent;
import com.pwb.backend.enums.OutboxStatus;
import com.pwb.backend.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends BaseRepository<OutboxEvent>, JpaSpecificationExecutor<OutboxEvent> {

    @Query(value = """
            SELECT id FROM outbox_events
             WHERE status = :status
               AND deleted = FALSE
               AND (next_retry_at IS NULL OR next_retry_at <= :now)
             ORDER BY created_at ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> claimPendingIds(@Param("status") String status,
                               @Param("batchSize") int batchSize,
                               @Param("now") Instant now);

    default List<UUID> claimPendingIds(OutboxStatus status, int batchSize, Instant now) {
        return claimPendingIds(status.name(), batchSize, now);
    }

    List<OutboxEvent> findByAggregateIdAndEventTypeAndDeletedFalse(UUID aggregateId, String eventType);
}
