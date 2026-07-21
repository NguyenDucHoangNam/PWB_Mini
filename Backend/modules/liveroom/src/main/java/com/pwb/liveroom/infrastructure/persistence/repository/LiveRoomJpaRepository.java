package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.core.model.LiveRoomStatus;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface LiveRoomJpaRepository
        extends JpaRepository<LiveRoomJpaEntity, UUID>, JpaSpecificationExecutor<LiveRoomJpaEntity> {

    Optional<LiveRoomJpaEntity> findByRoomCodeAndDeletedFalse(String roomCode);

    Page<LiveRoomJpaEntity> findByHostUserIdAndDeletedFalse(UUID hostUserId, Pageable pageable);

    Page<LiveRoomJpaEntity> findByHostUserIdAndStatusAndDeletedFalse(
            UUID hostUserId, LiveRoomStatus status, Pageable pageable);

    boolean existsByRoomCodeAndDeletedFalse(String roomCode);

    boolean existsByHostUserIdAndStatusAndDeletedFalse(UUID hostUserId, LiveRoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LiveRoomJpaEntity r WHERE r.roomCode = :roomCode AND r.deleted = false")
    Optional<LiveRoomJpaEntity> findByRoomCodeForUpdate(@Param("roomCode") String roomCode);
}