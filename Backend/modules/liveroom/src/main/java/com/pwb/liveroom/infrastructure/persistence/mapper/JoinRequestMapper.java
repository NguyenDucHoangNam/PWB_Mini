package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.JoinRequest;
import com.pwb.liveroom.infrastructure.persistence.entity.JoinRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class JoinRequestMapper {

    public JoinRequestJpaEntity toEntity(JoinRequest domain) {
        if (domain == null) {
            return null;
        }
        JoinRequestJpaEntity entity = JoinRequestJpaEntity.builder()
                .roomId(domain.getRoomId())
                .cycleId(domain.getCycleId())
                .userId(domain.getUserId())
                .userEmail(domain.getUserEmail())
                .idempotencyKey(domain.getIdempotencyKey())
                .build();
        applyTo(domain, entity);
        return entity;
    }


    public void applyTo(JoinRequest domain, JoinRequestJpaEntity target) {
        target.setState(domain.getState());
        target.setRejectionReason(domain.getRejectionReason());
        target.setDecidedAt(domain.getDecidedAt());
        target.setDecidedBy(domain.getDecidedBy());
    }

    public JoinRequest toDomain(JoinRequestJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        JoinRequest domain = JoinRequest.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getCycleId(),
                entity.getUserId(),
                entity.getUserEmail(),
                entity.getIdempotencyKey(),
                entity.getState(),
                entity.getRejectionReason(),
                entity.getDecidedAt(),
                entity.getDecidedBy()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}