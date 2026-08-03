package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.SongTagConfig;
import com.pwb.audio.infrastructure.persistence.entity.SongTagConfigJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SongTagConfigMapper {

    public SongTagConfigJpaEntity toEntity(SongTagConfig domain) {
        if (domain == null) {
            return null;
        }
        SongTagConfigJpaEntity entity = SongTagConfigJpaEntity.builder()
                .songId(domain.getSongId())
                .voiceTagId(domain.getVoiceTagId())
                .build();
        applyTo(domain, entity);
        return entity;
    }

    public void applyTo(SongTagConfig domain, SongTagConfigJpaEntity target) {
        target.setIntervalSeconds(domain.getIntervalSeconds());
        target.setVolumePercentage(domain.getVolumePercentage());
        target.setDuckingPercentage(domain.getDuckingPercentage());
        target.setStartOffsetSeconds(domain.getStartOffsetSeconds());
        target.setEnabled(domain.isEnabled());
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
                entity.getDuckingPercentage(),
                entity.getStartOffsetSeconds(),
                entity.isEnabled()
        );
    }
}
