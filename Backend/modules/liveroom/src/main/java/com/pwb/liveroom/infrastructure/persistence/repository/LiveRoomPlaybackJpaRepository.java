package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomPlaybackJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LiveRoomPlaybackJpaRepository
        extends JpaRepository<LiveRoomPlaybackJpaEntity, UUID> {

    Optional<LiveRoomPlaybackJpaEntity> findByRoomCodeAndDeletedFalse(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM LiveRoomPlaybackJpaEntity p
            WHERE p.roomCode = :roomCode AND p.deleted = false
            """)
    Optional<LiveRoomPlaybackJpaEntity> findByRoomCodeForUpdate(@Param("roomCode") String roomCode);
}
