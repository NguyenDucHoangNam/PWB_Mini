package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.Song;
import com.pwb.audio.infrastructure.persistence.entity.SongJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SongMapper {

    public SongJpaEntity toEntity(Song domain) {
        return toEntity(domain, null);
    }

    public SongJpaEntity toEntity(Song domain, SongJpaEntity existing) {
        if (domain == null) {
            return null;
        }

        if (existing != null) {
            existing.setTitle(domain.getTitle());
            existing.setArtist(domain.getArtist());
            existing.setAlbum(domain.getAlbum());
            existing.setOriginalS3Key(domain.getOriginalS3Key());
            existing.setProcessedS3Key(domain.getProcessedS3Key());
            existing.setFileSizeBytes(domain.getFileSizeBytes());
            existing.setDurationSeconds(domain.getDurationSeconds());
            existing.setFormat(domain.getFormat() != null ? domain.getFormat().value() : null);
            existing.setStatus(domain.getStatus());
            existing.setThumbnailUrl(domain.getThumbnailUrl());
            existing.setLastError(domain.getLastError());
            return existing;
        }

        return SongJpaEntity.builder()
                .userId(domain.getUserId())
                .title(domain.getTitle())
                .artist(domain.getArtist())
                .album(domain.getAlbum())
                .originalS3Key(domain.getOriginalS3Key())
                .processedS3Key(domain.getProcessedS3Key())
                .fileSizeBytes(domain.getFileSizeBytes())
                .durationSeconds(domain.getDurationSeconds())
                .format(domain.getFormat() != null ? domain.getFormat().value() : null)
                .status(domain.getStatus())
                .thumbnailUrl(domain.getThumbnailUrl())
                .lastError(domain.getLastError())
                .build();
    }

    public Song toDomain(SongJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Song.rehydrate(
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
    }
}
