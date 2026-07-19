package com.pwb.voice.core.model;

import com.pwb.voice.api.enums.VoiceTagType;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class VoiceTag {

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
            Integer durationSeconds,
            Long fileSizeBytes
    ) {
        return new VoiceTag(
                UUID.randomUUID(),
                userId,
                name,
                VoiceTagType.TTS,
                sourceText,
                languageCode,
                s3Key,
                durationSeconds,
                fileSizeBytes,
                false
        );
    }

    public static VoiceTag createUploadedTag(
            UUID userId,
            String name,
            String s3Key,
            Integer durationSeconds,
            Long fileSizeBytes
    ) {
        return new VoiceTag(
                UUID.randomUUID(),
                userId,
                name,
                VoiceTagType.UPLOADED,
                null,
                null,
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

    public void updateMetadata(String name) {
        if (name != null) this.name = name;
    }

    public void updateTtsParams(String sourceText, String languageCode) {
        if (sourceText != null) this.sourceText = sourceText;
        if (languageCode != null) this.languageCode = languageCode;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}