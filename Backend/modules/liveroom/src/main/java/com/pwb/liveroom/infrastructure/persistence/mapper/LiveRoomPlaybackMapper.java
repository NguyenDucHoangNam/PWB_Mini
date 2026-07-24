package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.core.model.LiveRoomPlayback;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomPlaybackJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomPlaybackMapper {

    public LiveRoomPlaybackJpaEntity toEntity(LiveRoomPlayback domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomPlaybackJpaEntity.builder()
                .roomCode(domain.getRoomCode())
                .songId(domain.getSongId())
                .songOwnerUserId(domain.getSongOwnerUserId())
                .status(domain.getStatus())
                .positionSeconds(domain.getPositionSeconds())
                .effectiveAt(domain.getEffectiveAt())
                .playbackVersion(domain.getPlaybackVersion())
                .changedByUserId(domain.getChangedByUserId())
                .changedAt(domain.getChangedAt())
                .playbackRate(domain.getPlaybackRate())
                .loopMode(domain.getLoopMode())
                .shuffleEnabled(domain.isShuffleEnabled())
                .build();
    }

    public LiveRoomPlaybackJpaEntity toEntity(LiveRoomPlayback domain, LiveRoomPlaybackJpaEntity existing) {
        if (domain == null) {
            return existing;
        }
        existing.setRoomCode(domain.getRoomCode());
        existing.setSongId(domain.getSongId());
        existing.setSongOwnerUserId(domain.getSongOwnerUserId());
        existing.setStatus(domain.getStatus());
        existing.setPositionSeconds(domain.getPositionSeconds());
        existing.setEffectiveAt(domain.getEffectiveAt());
        existing.setPlaybackVersion(domain.getPlaybackVersion());
        existing.setChangedByUserId(domain.getChangedByUserId());
        existing.setChangedAt(domain.getChangedAt());
        existing.setPlaybackRate(domain.getPlaybackRate());
        existing.setLoopMode(domain.getLoopMode());
        existing.setShuffleEnabled(domain.isShuffleEnabled());
        return existing;
    }

    public LiveRoomPlayback toDomain(LiveRoomPlaybackJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LiveRoomPlayback.rehydrate(
                entity.getId(),
                entity.getRoomCode(),
                entity.getSongId(),
                entity.getSongOwnerUserId(),
                entity.getStatus(),
                entity.getPositionSeconds(),
                entity.getEffectiveAt(),
                entity.getPlaybackVersion(),
                entity.getChangedByUserId(),
                entity.getChangedAt(),
                entity.getPlaybackRate(),
                entity.getLoopMode(),
                entity.isShuffleEnabled()
        );
    }
}
