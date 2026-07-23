package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomParticipantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveRoomParticipantJpaRepository
        extends JpaRepository<LiveRoomParticipantJpaEntity, UUID> {

    @Query("""
            SELECT p FROM LiveRoomParticipantJpaEntity p
            WHERE p.roomCode = :roomCode
              AND p.userId = :userId
              AND p.deleted = false
              AND p.leftAt IS NULL
            """)
    Optional<LiveRoomParticipantJpaEntity> findActiveByRoomAndUser(
            @Param("roomCode") String roomCode,
            @Param("userId") UUID userId);

    @Query("""
            SELECT p FROM LiveRoomParticipantJpaEntity p
            WHERE p.roomCode = :roomCode
              AND p.userId = :userId
              AND p.deleted = false
            ORDER BY p.joinedAt DESC
            """)
    List<LiveRoomParticipantJpaEntity> findByRoomCodeAndUserIdIncludeLeft(
            @Param("roomCode") String roomCode,
            @Param("userId") UUID userId);

    default Optional<LiveRoomParticipantJpaEntity> findFirstByRoomCodeAndUserIdIncludeLeft(
            String roomCode, UUID userId) {
        return findByRoomCodeAndUserIdIncludeLeft(roomCode, userId).stream().findFirst();
    }

    @Query("""
            SELECT p FROM LiveRoomParticipantJpaEntity p
            WHERE p.roomCode = :roomCode
              AND p.deleted = false
              AND p.leftAt IS NULL
            ORDER BY p.joinedAt ASC
            """)
    List<LiveRoomParticipantJpaEntity> findActiveByRoom(@Param("roomCode") String roomCode);

    @Query("""
            SELECT COUNT(p) FROM LiveRoomParticipantJpaEntity p
            WHERE p.roomCode = :roomCode
              AND p.deleted = false
              AND p.leftAt IS NULL
            """)
    long countActiveByRoom(@Param("roomCode") String roomCode);

    @Query("""
            UPDATE LiveRoomParticipantJpaEntity p
            SET p.leftAt = :leftAt,
                p.updatedAt = :leftAt
            WHERE p.roomCode = :roomCode
              AND p.deleted = false
              AND p.leftAt IS NULL
            """)
    @org.springframework.data.jpa.repository.Modifying
    int markAllLeftByRoom(@Param("roomCode") String roomCode, @Param("leftAt") java.time.Instant leftAt);
}