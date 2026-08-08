package com.pwb.liveroom.domain.repository;

import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.enums.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomRepository {

    LiveRoom save(LiveRoom room);

    Optional<LiveRoom> findById(UUID id);


    Optional<LiveRoom> findByIdForUpdate(UUID id);

    Optional<LiveRoom> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);


    boolean existsByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);

    Page<LiveRoom> findAllByOwnerId(UUID ownerId, Pageable pageable);

    Page<LiveRoom> findAllByOwnerIdAndStatus(UUID ownerId, RoomStatus status, Pageable pageable);


    List<LiveRoom> findActiveWithAbsentOwner();


    List<UUID> findEmptyRoomIds(Instant startedBefore);


    List<LiveRoom> findAllByIdIn(Collection<UUID> ids);


    Page<LiveRoom> search(RoomSearchCriteria criteria, Pageable pageable);
}