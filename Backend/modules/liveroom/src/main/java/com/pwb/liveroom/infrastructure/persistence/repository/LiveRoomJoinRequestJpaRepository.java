package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.core.model.JoinRequestStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJoinRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomJoinRequestJpaRepository
        extends JpaRepository<LiveRoomJoinRequestJpaEntity, UUID> {

    @Query("""
            SELECT r FROM LiveRoomJoinRequestJpaEntity r
            WHERE r.roomCode = :roomCode
              AND r.deleted = false
              AND r.status = :status
            ORDER BY r.createdAt ASC
            """)
    List<LiveRoomJoinRequestJpaEntity> findByRoomCodeAndStatus(
            @Param("roomCode") String roomCode,
            @Param("status") JoinRequestStatus status);

    @Query("""
            SELECT r FROM LiveRoomJoinRequestJpaEntity r
            WHERE r.roomCode = :roomCode
              AND r.userId = :userId
              AND r.deleted = false
              AND r.status = :status
            """)
    Optional<LiveRoomJoinRequestJpaEntity> findActiveByRoomAndUser(
            @Param("roomCode") String roomCode,
            @Param("userId") UUID userId,
            @Param("status") JoinRequestStatus status);

    @Query("""
            SELECT r FROM LiveRoomJoinRequestJpaEntity r
            WHERE r.id = :id AND r.deleted = false
            """)
    Optional<LiveRoomJoinRequestJpaEntity> findActiveById(@Param("id") UUID id);
}
