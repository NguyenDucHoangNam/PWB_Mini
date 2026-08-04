package com.pwb.liveroom.infrastructure.persistence.repository;

import com.pwb.liveroom.infrastructure.persistence.entity.PlaybackStateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlaybackStateJpaRepository extends JpaRepository<PlaybackStateJpaEntity, UUID> {

    Optional<PlaybackStateJpaEntity> findByRoomId(UUID roomId);

    void deleteByRoomId(UUID roomId);
}