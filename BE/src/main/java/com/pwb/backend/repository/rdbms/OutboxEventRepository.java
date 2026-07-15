package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.OutboxEvent;
import com.pwb.backend.enums.OutboxStatus;
import com.pwb.backend.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends BaseRepository<OutboxEvent>, JpaSpecificationExecutor<OutboxEvent> {

    @Query(value = """
            SELECT * FROM outbox_events
             WHERE status = :status
               AND deleted = FALSE
             ORDER BY created_at ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingBatch(@Param("status") String status, @Param("batchSize") int batchSize);

    default List<OutboxEvent> claimPendingBatch(OutboxStatus status, int batchSize) {
        return claimPendingBatch(status.name(), batchSize);
    }

    List<OutboxEvent> findByAggregateIdAndEventTypeAndDeletedFalse(UUID aggregateId, String eventType);
}