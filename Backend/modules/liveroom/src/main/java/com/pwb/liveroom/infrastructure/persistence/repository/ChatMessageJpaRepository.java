package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.ChatMessageJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

    Optional<ChatMessageJpaEntity> findByIdAndCycleId(UUID id, UUID cycleId);

    List<ChatMessageJpaEntity> findAllByCycleIdOrderBySentAtDescIdDesc(UUID cycleId, Pageable pageable);


    @Query("""
            SELECT m FROM ChatMessageJpaEntity m
             WHERE m.cycleId = :cycleId
               AND (m.sentAt < :beforeSentAt
                    OR (m.sentAt = :beforeSentAt AND m.id < :beforeId))
             ORDER BY m.sentAt DESC, m.id DESC
            """)
    List<ChatMessageJpaEntity> findOlderThan(
            @Param("cycleId") UUID cycleId,
            @Param("beforeSentAt") Instant beforeSentAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessageJpaEntity m WHERE m.sentAt < :threshold")
    int deleteSentBefore(@Param("threshold") Instant threshold);
}