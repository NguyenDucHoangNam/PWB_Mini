package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class VoiceTagMapper {

    public VoiceTagJpaEntity toEntity(VoiceTag domain) {
        return toEntity(domain, null);
    }

    public VoiceTagJpaEntity toEntity(VoiceTag domain, VoiceTagJpaEntity existing) {
        if (domain == null) {
            return null;
        }

        if (existing != null) {
            existing.setName(domain.getName());
            existing.setTagType(domain.getTagType());
            existing.setSourceText(domain.getSourceText());
            existing.setLanguageCode(domain.getLanguageCode());
            existing.setS3Key(domain.getS3Key());
            existing.setDurationSeconds(domain.getDurationSeconds());
            existing.setFileSizeBytes(domain.getFileSizeBytes());
            existing.setDefault(domain.isDefault());
            return existing;
        }

        return VoiceTagJpaEntity.builder()
                .userId(domain.getUserId())
                .name(domain.getName())
                .tagType(domain.getTagType())
                .sourceText(domain.getSourceText())
                .languageCode(domain.getLanguageCode())
                .s3Key(domain.getS3Key())
                .durationSeconds(domain.getDurationSeconds())
                .fileSizeBytes(domain.getFileSizeBytes())
                .isDefault(domain.isDefault())
                .build();
    }

    public VoiceTag toDomain(VoiceTagJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return VoiceTag.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getTagType(),
                entity.getSourceText(),
                entity.getLanguageCode(),
                entity.getS3Key(),
                entity.getDurationSeconds(),
                entity.getFileSizeBytes(),
                entity.isDefault()
        );
    }
}
