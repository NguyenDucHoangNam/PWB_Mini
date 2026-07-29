package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SongTagConfigMapper {

    public SongTagConfigJpaEntity toEntity(SongTagConfig domain) {
        return toEntity(domain, null);
    }

    public SongTagConfigJpaEntity toEntity(SongTagConfig domain, SongTagConfigJpaEntity existing) {
        if (domain == null) {
            return null;
        }

        if (existing != null) {
            existing.setIntervalSeconds(domain.getIntervalSeconds());
            existing.setVolumePercentage(domain.getVolumePercentage());
            existing.setFadeInDurationMs(domain.getFadeInDurationMs());
            existing.setFadeOutDurationMs(domain.getFadeOutDurationMs());
            existing.setStartOffsetSeconds(domain.getStartOffsetSeconds());
            existing.setEnabled(domain.isEnabled());
            return existing;
        }

        return SongTagConfigJpaEntity.builder()
                .songId(domain.getSongId())
                .voiceTagId(domain.getVoiceTagId())
                .intervalSeconds(domain.getIntervalSeconds())
                .volumePercentage(domain.getVolumePercentage())
                .fadeInDurationMs(domain.getFadeInDurationMs())
                .fadeOutDurationMs(domain.getFadeOutDurationMs())
                .startOffsetSeconds(domain.getStartOffsetSeconds())
                .enabled(domain.isEnabled())
                .build();
    }

    public SongTagConfig toDomain(SongTagConfigJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return SongTagConfig.rehydrate(
                entity.getId(),
                entity.getSongId(),
                entity.getVoiceTagId(),
                entity.getIntervalSeconds(),
                entity.getVolumePercentage(),
                entity.getFadeInDurationMs(),
                entity.getFadeOutDurationMs(),
                entity.getStartOffsetSeconds(),
                entity.isEnabled()
        );
    }
}
