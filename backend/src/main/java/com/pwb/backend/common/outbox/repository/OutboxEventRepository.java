package com.pwb.backend.common.outbox.repository;

import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status IN :statuses
            AND e.nextAttemptAt <= :now
            ORDER BY e.nextAttemptAt ASC
            """)
    List<OutboxEvent> findDueForRetry(
            @Param("statuses") Set<OutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);
}
