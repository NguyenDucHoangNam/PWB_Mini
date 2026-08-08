package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.Participant;
import com.pwb.liveroom.infrastructure.persistence.entity.ParticipantJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ParticipantMapper {

    public ParticipantJpaEntity toEntity(Participant domain) {
        if (domain == null) {
            return null;
        }
        ParticipantJpaEntity entity = ParticipantJpaEntity.builder()
                .roomId(domain.getRoomId())
                .cycleId(domain.getCycleId())
                .userId(domain.getUserId())
                .userEmail(domain.getUserEmail())
                .roomRole(domain.getRoomRole())
                .build();
        applyTo(domain, entity);
        return entity;
    }


    public void applyTo(Participant domain, ParticipantJpaEntity target) {
        target.setState(domain.getState());
        target.setJoinedAt(domain.getJoinedAt());
        target.setLeftAt(domain.getLeftAt());
        target.setCameraOn(domain.isCameraOn());
        target.setMicOn(domain.isMicOn());
        target.setMicState(domain.getMicState());
        target.setMicMutedByOwnerAt(domain.getMicMutedByOwnerAt());
        target.setMicUnmuteCooldownUntil(domain.getMicUnmuteCooldownUntil());
        target.setLastInteractionAt(domain.getLastInteractionAt());
    }

    public Participant toDomain(ParticipantJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Participant domain = Participant.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getCycleId(),
                entity.getUserId(),
                entity.getUserEmail(),
                entity.getRoomRole(),
                entity.getState(),
                entity.getJoinedAt(),
                entity.getLeftAt(),
                entity.isCameraOn(),
                entity.isMicOn(),
                entity.getMicState(),
                entity.getMicMutedByOwnerAt(),
                entity.getMicUnmuteCooldownUntil(),
                entity.getLastInteractionAt()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}