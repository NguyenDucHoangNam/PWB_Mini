package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.RoomAdminActionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomAdminActionJpaRepository extends JpaRepository<RoomAdminActionJpaEntity, UUID> {

    List<RoomAdminActionJpaEntity> findAllByRoomIdOrderByCreatedAtDesc(UUID roomId);
}