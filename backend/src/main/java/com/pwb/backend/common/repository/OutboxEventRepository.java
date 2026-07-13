package com.pwb.backend.common.repository;

import com.pwb.backend.common.model.OutboxEvent;
import com.pwb.backend.common.outbox.enums.OutboxStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
        select e from OutboxEvent e
        where e.status in :statuses
          and e.nextAttemptAt <= :now
        order by e.nextAttemptAt asc
        """)
    List<OutboxEvent> findDueForRetry(@Param("statuses") Collection<OutboxStatus> statuses,
                                      @Param("now") Instant now,
                                      Pageable pageable);

    default List<OutboxEvent> findDueForRetry(Collection<OutboxStatus> statuses, Instant now, int limit) {
        return findDueForRetry(statuses, now, Pageable.ofSize(limit));
    }
}
