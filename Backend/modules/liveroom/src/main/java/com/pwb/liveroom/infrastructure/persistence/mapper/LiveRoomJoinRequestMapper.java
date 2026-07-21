package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.core.model.LiveRoomJoinRequest;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJoinRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomJoinRequestMapper {

    public LiveRoomJoinRequestJpaEntity toEntity(LiveRoomJoinRequest domain) {
        if (domain == null) {
            return null;
        }
        return LiveRoomJoinRequestJpaEntity.builder()
                .roomCode(domain.getRoomCode())
                .userId(domain.getUserId())
                .displayName(domain.getDisplayName())
                .message(domain.getMessage())
                .status(domain.getStatus())
                .decisionReason(domain.getDecisionReason())
                .decidedByUserId(domain.getDecidedByUserId())
                .decidedAt(domain.getDecidedAt())
                .build();
    }

    public LiveRoomJoinRequestJpaEntity toEntity(LiveRoomJoinRequest domain, LiveRoomJoinRequestJpaEntity existing) {
        if (domain == null) {
            return existing;
        }
        existing.setDisplayName(domain.getDisplayName());
        existing.setMessage(domain.getMessage());
        existing.setStatus(domain.getStatus());
        existing.setDecisionReason(domain.getDecisionReason());
        existing.setDecidedByUserId(domain.getDecidedByUserId());
        existing.setDecidedAt(domain.getDecidedAt());
        return existing;
    }

    public LiveRoomJoinRequest toDomain(LiveRoomJoinRequestJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return LiveRoomJoinRequest.rehydrate(
                entity.getId(),
                entity.getRoomCode(),
                entity.getUserId(),
                entity.getDisplayName(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getDecisionReason(),
                entity.getDecidedByUserId(),
                entity.getDecidedAt(),
                entity.getCreatedAt()
        );
    }
}
