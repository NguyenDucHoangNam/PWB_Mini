package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.domain.enums.JoinRequestState;
import com.pwb.liveroom.infrastructure.persistence.entity.JoinRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestJpaRepository extends JpaRepository<JoinRequestJpaEntity, UUID> {

    Optional<JoinRequestJpaEntity> findByRoomIdAndUserIdAndIdempotencyKey(
            UUID roomId, UUID userId, String idempotencyKey);

    Optional<JoinRequestJpaEntity> findByRoomIdAndUserIdAndState(
            UUID roomId, UUID userId, JoinRequestState state);

    List<JoinRequestJpaEntity> findAllByRoomIdAndStateOrderByCreatedAtAsc(UUID roomId, JoinRequestState state);

    void deleteAllByRoomId(UUID roomId);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE JoinRequestJpaEntity j
               SET j.idempotencyKey = CONCAT('expired:', CAST(j.id AS string))
             WHERE j.createdAt < :expiredBefore
               AND j.idempotencyKey NOT LIKE 'expired:%'
            """)
    int expireIdempotencyKeys(@Param("expiredBefore") Instant expiredBefore);
}