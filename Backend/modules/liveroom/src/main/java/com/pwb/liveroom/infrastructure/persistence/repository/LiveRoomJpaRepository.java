package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.domain.enums.RoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomJpaRepository extends JpaRepository<LiveRoomJpaEntity, UUID> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LiveRoomJpaEntity r WHERE r.id = :id")
    Optional<LiveRoomJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<LiveRoomJpaEntity> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    boolean existsByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);

    Page<LiveRoomJpaEntity> findAllByOwnerId(UUID ownerId, Pageable pageable);

    Page<LiveRoomJpaEntity> findAllByOwnerIdAndStatus(UUID ownerId, RoomStatus status, Pageable pageable);

    @Query("""
            SELECT r FROM LiveRoomJpaEntity r
             WHERE r.status = com.pwb.liveroom.domain.enums.RoomStatus.ACTIVE
               AND r.ownerLeftAt IS NOT NULL
            """)
    List<LiveRoomJpaEntity> findActiveWithAbsentOwner();


    @Query("""
            SELECT r.id FROM LiveRoomJpaEntity r
              JOIN RoomSessionCycleJpaEntity c ON c.id = r.currentCycleId
             WHERE r.status = com.pwb.liveroom.domain.enums.RoomStatus.ACTIVE
               AND r.currentParticipantCount = 0
               AND r.ownerLeftAt IS NULL
               AND c.startedAt < :startedBefore
            """)
    List<UUID> findEmptyRoomIds(@Param("startedBefore") Instant startedBefore);
}