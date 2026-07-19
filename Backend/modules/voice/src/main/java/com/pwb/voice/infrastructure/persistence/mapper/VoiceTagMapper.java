package com.pwb.voice.infrastructure.persistence.mapper;

import com.pwb.voice.core.model.VoiceTag;
import com.pwb.voice.infrastructure.persistence.entity.VoiceTagJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface VoiceTagMapper {

    default VoiceTagJpaEntity toEntity(VoiceTag domain) {
        if (domain == null) {
            return null;
        }
        return VoiceTagJpaEntity.builder()
                .userId(domain.getUserId())
                .name(domain.getName())
                .description(domain.getDescription())
                .tagType(domain.getTagType())
                .sourceText(domain.getSourceText())
                .languageCode(domain.getLanguageCode())
                .s3Key(domain.getS3Key())
                .durationSeconds(domain.getDurationSeconds())
                .fileSizeBytes(domain.getFileSizeBytes())
                .isDefault(domain.isDefault())
                .build();
    }

    default VoiceTagJpaEntity toEntity(VoiceTag domain, VoiceTagJpaEntity existing) {
        if (domain == null) {
            return existing;
        }
        existing.setUserId(domain.getUserId());
        existing.setName(domain.getName());
        existing.setDescription(domain.getDescription());
        existing.setTagType(domain.getTagType());
        existing.setSourceText(domain.getSourceText());
        existing.setLanguageCode(domain.getLanguageCode());
        existing.setS3Key(domain.getS3Key());
        existing.setDurationSeconds(domain.getDurationSeconds());
        existing.setFileSizeBytes(domain.getFileSizeBytes());
        existing.setDefault(domain.isDefault());
        return existing;
    }

    default VoiceTag toDomain(VoiceTagJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return VoiceTag.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getDescription(),
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
