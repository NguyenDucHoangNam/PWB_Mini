package com.pwb.audio.infrastructure.persistence.mapper;

import com.pwb.audio.domain.model.VoiceTag;
import com.pwb.audio.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class VoiceTagMapper {

    /** Builds a brand-new row; the id is left to the JPA generator. */
    public VoiceTagJpaEntity toEntity(VoiceTag domain) {
        if (domain == null) {
            return null;
        }
        VoiceTagJpaEntity entity = VoiceTagJpaEntity.builder()
                .userId(domain.getUserId())
                .tagType(domain.getTagType())
                .build();
        applyTo(domain, entity);
        return entity;
    }

    /** Copies the mutable state of the aggregate onto a managed row. */
    public void applyTo(VoiceTag domain, VoiceTagJpaEntity target) {
        target.setName(domain.getName());
        target.setSourceText(domain.getSourceText());
        target.setLanguageCode(domain.getLanguageCode());
        target.setVoiceName(domain.getVoiceName());
        target.setS3Key(domain.getS3Key());
        target.setDurationSeconds(domain.getDurationSeconds());
        target.setFileSizeBytes(domain.getFileSizeBytes());
        target.setDefault(domain.isDefault());
    }

    public VoiceTag toDomain(VoiceTagJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        VoiceTag domain = VoiceTag.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getTagType(),
                entity.getSourceText(),
                entity.getLanguageCode(),
                entity.getVoiceName(),
                entity.getS3Key(),
                entity.getDurationSeconds(),
                entity.getFileSizeBytes(),
                entity.isDefault()
        );
        domain.restoreAuditTimestamps(entity.getCreatedAt(), entity.getUpdatedAt());
        return domain;
    }
}
