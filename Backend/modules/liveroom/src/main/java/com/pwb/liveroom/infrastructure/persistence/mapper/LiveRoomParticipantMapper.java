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
                entity.getLeftAt()
        );
    }
}