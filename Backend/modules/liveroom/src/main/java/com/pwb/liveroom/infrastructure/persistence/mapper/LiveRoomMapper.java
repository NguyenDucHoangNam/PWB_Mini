package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.infrastructure.persistence.entity.LiveRoomJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LiveRoomMapper {


    public LiveRoomJpaEntity toEntity(LiveRoom domain) {
        if (domain == null) {
            return null;
        }
        LiveRoomJpaEntity entity = LiveRoomJpaEntity.builder()
                .ownerId(domain.getOwnerId())
                .roomCode(domain.getRoomCode().value())
                .build();
        applyTo(domain, entity);
        return entity;
    }


    public void applyTo(LiveRoom domain, LiveRoomJpaEntity target) {
        target.setRoomName(domain.getRoomName().value());
        target.setNormalizedName(domain.getRoomName().normalized());
        target.setStatus(domain.getStatus());
        target.setMaxParticipants(domain.getMaxParticipants());
        target.setOwnerGraceSeconds(domain.getOwnerGraceSeconds());
        target.setCurrentParticipantCount(domain.getCurrentParticipantCount());
        target.setReservedOwnerSlot(domain.isReservedOwnerSlot());
        target.setOwnerLeftAt(domain.getOwnerLeftAt());
        target.setCurrentCycleId(domain.getCurrentCycleId());
        target.setReopenedCount(domain.getReopenedCount());
        target.setLastReopenedAt(domain.getLastReopenedAt());
        target.setPreviousEndedAt(domain.getPreviousEndedAt());
        target.setEndedAt(domain.getEndedAt());
        target.setEndedReason(domain.getEndedReason());
    }

    public LiveRoom toDomain(LiveRoomJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        LiveRoom domain = LiveRoom.rehydrate(
                entity.getId(),
                entity.getOwnerId(),
                entity.getRoomCode(),
                entity.getRoomName(),
                entity.getStatus(),
                entity.getMaxParticipants(),
                entity.getOwnerGraceSeconds(),
                entity.getCurrentParticipantCount(),
                entity.isReservedOwnerSlot(),
                entity.getOwnerLeftAt(),
                entity.getCurrentCycleId(),
                entity.getReopenedCount(),
                entity.getLastReopenedAt(),
                entity.getPreviousEndedAt(),
                entity.getEndedAt(),
                entity.getEndedReason()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}