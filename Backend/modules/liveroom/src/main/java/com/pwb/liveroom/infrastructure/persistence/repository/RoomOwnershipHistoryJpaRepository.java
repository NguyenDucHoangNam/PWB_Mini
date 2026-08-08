package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.RoomOwnershipHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomOwnershipHistoryJpaRepository extends JpaRepository<RoomOwnershipHistoryJpaEntity, UUID> {

    List<RoomOwnershipHistoryJpaEntity> findAllByRoomIdOrderByChangedAtDesc(UUID roomId);
}