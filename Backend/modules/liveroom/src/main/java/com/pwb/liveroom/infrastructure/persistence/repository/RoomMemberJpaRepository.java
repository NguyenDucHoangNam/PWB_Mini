package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.RoomMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoomMemberJpaRepository extends JpaRepository<RoomMemberJpaEntity, UUID> {

    Optional<RoomMemberJpaEntity> findByRoomIdAndUserId(UUID roomId, UUID userId);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RoomMemberJpaEntity m
               SET m.rejectCountByOwner = 0,
                   m.rejectCountByCapacity = 0
             WHERE m.roomId = :roomId
               AND (m.rejectCountByOwner > 0 OR m.rejectCountByCapacity > 0)
            """)
    void resetRejectCountersForRoom(@Param("roomId") UUID roomId);
}