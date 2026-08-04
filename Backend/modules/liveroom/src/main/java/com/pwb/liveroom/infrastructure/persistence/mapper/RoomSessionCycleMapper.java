package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.RoomSessionCycle;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomSessionCycleJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RoomSessionCycleMapper {

    public RoomSessionCycleJpaEntity toEntity(RoomSessionCycle domain) {
        if (domain == null) {
            return null;
        }
        RoomSessionCycleJpaEntity entity = RoomSessionCycleJpaEntity.builder()
                .roomId(domain.getRoomId())
                .cycleNumber(domain.getCycleNumber())
                .startedAt(domain.getStartedAt())
                .build();
        applyTo(domain, entity);
        return entity;
    }


    public void applyTo(RoomSessionCycle domain, RoomSessionCycleJpaEntity target) {
        target.setEndedAt(domain.getEndedAt());
        target.setEndedReason(domain.getEndedReason());
    }

    public RoomSessionCycle toDomain(RoomSessionCycleJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        RoomSessionCycle domain = RoomSessionCycle.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getCycleNumber(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getEndedReason()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}