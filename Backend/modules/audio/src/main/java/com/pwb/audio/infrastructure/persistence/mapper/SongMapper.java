package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SongMapper {

    /** Builds a brand-new row; the id is left to the JPA generator. */
    public SongJpaEntity toEntity(Song domain) {
        if (domain == null) {
            return null;
        }
        SongJpaEntity entity = SongJpaEntity.builder()
                .userId(domain.getUserId())
                .build();
        applyTo(domain, entity);
        return entity;
    }

    /** Copies the mutable state of the aggregate onto a managed row. */
    public void applyTo(Song domain, SongJpaEntity target) {
        target.setTitle(domain.getTitle());
        target.setArtist(domain.getArtist());
        target.setAlbum(domain.getAlbum());
        target.setOriginalS3Key(domain.getOriginalS3Key());
        target.setProcessedS3Key(domain.getProcessedS3Key());
        target.setFileSizeBytes(domain.getFileSizeBytes());
        target.setDurationSeconds(domain.getDurationSeconds());
        target.setFormat(domain.getFormat() != null ? domain.getFormat().value() : null);
        target.setStatus(domain.getStatus());
        target.setThumbnailUrl(domain.getThumbnailUrl());
        target.setLastError(domain.getLastError());
    }

    public Song toDomain(SongJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Song domain = Song.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getAlbum(),
                entity.getOriginalS3Key(),
                entity.getProcessedS3Key(),
                entity.getFileSizeBytes(),
                entity.getDurationSeconds(),
                entity.getFormat(),
                entity.getStatus(),
                entity.getThumbnailUrl(),
                entity.getLastError()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}
