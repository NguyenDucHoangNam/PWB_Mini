package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.core.model.LiveRoom;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomMapper {

    public LiveRoomJpaEntity toEntity(LiveRoom domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomJpaEntity.builder()
                .hostUserId(domain.getHostUserId())
                .roomCode(domain.getRoomCode())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .mode(domain.getMode())
                .passwordHash(domain.getPasswordHash())
                .maxParticipants(domain.getMaxParticipants())
                .status(domain.getStatus())
                .currentParticipantCount(domain.getCurrentParticipantCount())
                .scheduledStartAt(domain.getScheduledStartAt())
                .startedAt(domain.getStartedAt())
                .endedAt(domain.getEndedAt())
                .build();
    }

    public LiveRoomJpaEntity toEntity(LiveRoom domain, LiveRoomJpaEntity existing) {
        if (domain == null) {
            return existing;
        }
        existing.setHostUserId(domain.getHostUserId());
        existing.setRoomCode(domain.getRoomCode());
        existing.setTitle(domain.getTitle());
        existing.setDescription(domain.getDescription());
        existing.setMode(domain.getMode());
        existing.setPasswordHash(domain.getPasswordHash());
        existing.setMaxParticipants(domain.getMaxParticipants());
        existing.setStatus(domain.getStatus());
        existing.setCurrentParticipantCount(domain.getCurrentParticipantCount());
        existing.setScheduledStartAt(domain.getScheduledStartAt());
        existing.setStartedAt(domain.getStartedAt());
        existing.setEndedAt(domain.getEndedAt());
        return existing;
    }

    public LiveRoom toDomain(LiveRoomJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LiveRoom.rehydrate(
                entity.getId(),
                entity.getHostUserId(),
                entity.getRoomCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getMode(),
                entity.getPasswordHash(),
                entity.getMaxParticipants(),
                entity.getStatus(),
                entity.getCurrentParticipantCount(),
                entity.getScheduledStartAt(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt()
        );
    }
}