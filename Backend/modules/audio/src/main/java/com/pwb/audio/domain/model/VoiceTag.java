package com.pwb.audio.domain.model;

import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class VoiceTag extends DomainBaseEntity {

    private final UUID id;
    private final UUID userId;
    private String name;
    private VoiceTagType tagType;
    private String sourceText;
    private String languageCode;
    private String s3Key;
    private Integer durationSeconds;
    private Long fileSizeBytes;
    private boolean isDefault;

    private VoiceTag(
            UUID id,
            UUID userId,
            String name,
            VoiceTagType tagType,
            String sourceText,
            String languageCode,
            String s3Key,
            Integer durationSeconds,
            Long fileSizeBytes,
            boolean isDefault
    ) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.tagType = tagType;
        this.sourceText = sourceText;
        this.languageCode = languageCode;
        this.s3Key = s3Key;
        this.durationSeconds = durationSeconds;
        this.fileSizeBytes = fileSizeBytes;
        this.isDefault = isDefault;
    }

    public static VoiceTag createTtsTag(
            UUID userId,
            String name,
            String sourceText,
            String languageCode,
            String s3Key,
            Integer durationSeconds
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException("sourceText must not be blank for TTS tag");
        }
        return new VoiceTag(
                UUID.randomUUID(),
                userId,
                name,
                VoiceTagType.TTS,
                sourceText,
                languageCode,
                s3Key,
                durationSeconds,
                null,
                false
        );
    }

    public static VoiceTag rehydrate(
            UUID id,
            UUID userId,
            String name,
            VoiceTagType tagType,
            String sourceText,
            String languageCode,
            String s3Key,
            Integer durationSeconds,
            Long fileSizeBytes,
            boolean isDefault
    ) {
        return new VoiceTag(
                id,
                userId,
                name,
                tagType,
                sourceText,
                languageCode,
                s3Key,
                durationSeconds,
                fileSizeBytes,
                isDefault
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public VoiceTagType getTagType() {
        return tagType;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getS3Key() {
        return s3Key;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void updateMetadata(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        touch();
    }

    public void updateTtsParams(String sourceText, String languageCode) {
        if (this.tagType != VoiceTagType.TTS) {
            throw new IllegalStateException("Can only update TTS params for TTS type tags");
        }
        this.sourceText = sourceText;
        this.languageCode = languageCode;
        touch();
    }

    public void markDeleted() {
        this.isDefault = false;
        touch();
    }

    public boolean isTts() {
        return this.tagType == VoiceTagType.TTS;
    }

    public boolean isUploaded() {
        return this.tagType == VoiceTagType.UPLOADED;
    }
}
