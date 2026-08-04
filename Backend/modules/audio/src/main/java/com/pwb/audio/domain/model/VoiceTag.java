package com.pwb.audio.domain.model;

import com.pwb.audio.domain.enums.VoiceTagType;
import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class VoiceTag extends DomainBaseEntity {

    private final UUID id;
    private final UUID userId;
    private String name;
    private final VoiceTagType tagType;
    private String sourceText;
    private String languageCode;
    private String voiceName;
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
            String voiceName,
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
        this.voiceName = voiceName;
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
            String voiceName,
            String s3Key,
            Integer durationSeconds,
            Long fileSizeBytes
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
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalArgumentException("s3Key must not be blank");
        }
        // id stays null until the row is persisted; that is what marks this instance as new.
        return new VoiceTag(
                null,
                userId,
                name,
                VoiceTagType.TTS,
                sourceText,
                languageCode,
                voiceName,
                s3Key,
                durationSeconds,
                fileSizeBytes,
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
            String voiceName,
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
                voiceName,
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

    public String getVoiceName() {
        return voiceName;
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

    public boolean isNew() {
        return id == null;
    }

    public void updateMetadata(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        touch();
    }
}
