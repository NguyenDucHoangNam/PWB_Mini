package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.RoomMember;
import com.pwb.liveroom.infrastructure.persistence.entity.RoomMemberJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RoomMemberMapper {

    public RoomMemberJpaEntity toEntity(RoomMember domain) {
        if (domain == null) {
            return null;
        }
        RoomMemberJpaEntity entity = RoomMemberJpaEntity.builder()
                .roomId(domain.getRoomId())
                .userId(domain.getUserId())
                .build();
        applyTo(domain, entity);
        return entity;
    }

    public void applyTo(RoomMember domain, RoomMemberJpaEntity target) {
        target.setWasApproved(domain.wasApproved());
        target.setKickedAt(domain.getKickedAt());
        target.setKickedCooldownUntil(domain.getKickedCooldownUntil());
        target.setRejectCountByOwner(domain.getRejectCountByOwner());
        target.setRejectCountByCapacity(domain.getRejectCountByCapacity());
    }

    public RoomMember toDomain(RoomMemberJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        RoomMember domain = RoomMember.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getUserId(),
                entity.isWasApproved(),
                entity.getKickedAt(),
                entity.getKickedCooldownUntil(),
                entity.getRejectCountByOwner(),
                entity.getRejectCountByCapacity()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}