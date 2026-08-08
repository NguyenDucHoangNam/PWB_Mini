package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.RoomSessionCycleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoomSessionCycleJpaRepository extends JpaRepository<RoomSessionCycleJpaEntity, UUID> {

    Optional<RoomSessionCycleJpaEntity> findByRoomIdAndEndedAtIsNull(UUID roomId);

    @Query("SELECT COALESCE(MAX(c.cycleNumber), 0) FROM RoomSessionCycleJpaEntity c WHERE c.roomId = :roomId")
    int findHighestCycleNumber(@Param("roomId") UUID roomId);
}