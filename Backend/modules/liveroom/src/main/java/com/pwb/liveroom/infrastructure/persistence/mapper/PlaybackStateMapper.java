package com.pwb.liveroom.infrastructure.persistence.mapper;

import com.pwb.liveroom.domain.model.PlaybackState;
import com.pwb.liveroom.infrastructure.persistence.entity.PlaybackStateJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PlaybackStateMapper {

    public PlaybackStateJpaEntity toEntity(PlaybackState domain) {
        if (domain == null) {
            return null;
        }
        PlaybackStateJpaEntity entity = PlaybackStateJpaEntity.builder()
                .roomId(domain.getRoomId())
                .build();
        applyTo(domain, entity);
        return entity;
    }


    public void applyTo(PlaybackState domain, PlaybackStateJpaEntity target) {
        target.setSongId(domain.getSongId());
        target.setSongOwnerId(domain.getSongOwnerId());
        target.setSongTitle(domain.getSongTitle());
        target.setSongArtist(domain.getSongArtist());
        target.setSongDurationSeconds(domain.getSongDurationSeconds());
        target.setStatus(domain.getStatus());
        target.setPositionSeconds(domain.getPositionSeconds());
        target.setVolumePercent(domain.getVolumePercent());
        target.setStartedAt(domain.getStartedAt());
        target.setLastUpdatedAt(domain.getLastUpdatedAt());
        target.setLastUpdatedBy(domain.getLastUpdatedBy());
        target.setSequenceNumber(domain.getSequenceNumber());
    }

    public PlaybackState toDomain(PlaybackStateJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        PlaybackState domain = PlaybackState.rehydrate(
                entity.getId(),
                entity.getRoomId(),
                entity.getSongId(),
                entity.getSongOwnerId(),
                entity.getSongTitle(),
                entity.getSongArtist(),
                entity.getSongDurationSeconds(),
                entity.getStatus(),
                entity.getPositionSeconds(),
                entity.getVolumePercent(),
                entity.getStartedAt(),
                entity.getLastUpdatedAt(),
                entity.getLastUpdatedBy(),
                entity.getSequenceNumber()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}