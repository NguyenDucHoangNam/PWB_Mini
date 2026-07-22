package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.core.model.LiveRoomParticipant;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomParticipantJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomParticipantMapper {

    public LiveRoomParticipantJpaEntity toEntity(LiveRoomParticipant domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomParticipantJpaEntity.builder()
                .roomCode(domain.getRoomCode())
                .userId(domain.getUserId())
                .displayName(domain.getDisplayName())
                .roleAtJoin(domain.getRoleAtJoin())
                .joinedAt(domain.getJoinedAt())
                .leftAt(domain.getLeftAt())
                .micMuted(domain.isMicMuted())
                .cameraOff(domain.isCameraOff())
                .lastSeenAt(domain.getLastSeenAt())
                .build();
    }

    public LiveRoomParticipantJpaEntity toEntity(LiveRoomParticipant domain, LiveRoomParticipantJpaEntity existing) {
        if (domain == null) {
            return existing;
        }
        existing.setRoomCode(domain.getRoomCode());
        existing.setUserId(domain.getUserId());
        existing.setDisplayName(domain.getDisplayName());
        existing.setRoleAtJoin(domain.getRoleAtJoin());
        existing.setJoinedAt(domain.getJoinedAt());
        existing.setLeftAt(domain.getLeftAt());
        existing.setMicMuted(domain.isMicMuted());
        existing.setCameraOff(domain.isCameraOff());
        existing.setLastSeenAt(domain.getLastSeenAt());
        return existing;
    }

    public LiveRoomParticipant toDomain(LiveRoomParticipantJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LiveRoomParticipant.rehydrate(
                entity.getId(),
                entity.getRoomCode(),
                entity.getUserId(),
                entity.getDisplayName(),
                entity.getRoleAtJoin(),
                entity.getJoinedAt(),
                entity.getLeftAt(),
                Boolean.TRUE.equals(entity.getMicMuted()),
                Boolean.TRUE.equals(entity.getCameraOff()),
                entity.getLastSeenAt()
        );
    }
}
