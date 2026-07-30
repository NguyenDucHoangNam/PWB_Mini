package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.VoiceTagView;
import com.pwb.audio.domain.enums.VoiceTagType;

import java.time.Instant;
import java.util.UUID;

public record VoiceTagResponse(
        UUID id,
        UUID userId,
        String name,
        VoiceTagType tagType,
        String sourceText,
        String languageCode,
        String s3Key,
        Integer durationSeconds,
        Long fileSizeBytes,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {

    public static VoiceTagResponse from(VoiceTagView view) {
        return new VoiceTagResponse(
                view.id(),
                view.userId(),
                view.name(),
                view.tagType(),
                view.sourceText(),
                view.languageCode(),
                view.s3Key(),
                view.durationSeconds(),
                view.fileSizeBytes(),
                view.isDefault(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
