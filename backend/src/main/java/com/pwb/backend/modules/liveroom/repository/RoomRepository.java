package com.pwb.backend.modules.liveroom.repository;

import com.pwb.backend.modules.liveroom.entity.Room;
import com.pwb.backend.modules.liveroom.enums.RoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByRoomCodeAndStatus(String roomCode, RoomStatus status);

    Optional<Room> findFirstByHostIdAndStatusOrderByCreatedAtDesc(UUID hostId, RoomStatus status);

    List<Room> findByStatusAndCreatedAtBefore(RoomStatus status, Instant threshold, Pageable pageable);
}