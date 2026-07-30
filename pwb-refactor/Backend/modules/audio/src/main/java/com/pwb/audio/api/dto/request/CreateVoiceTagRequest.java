package com.pwb.audio.api.dto.request;

import com.pwb.audio.domain.enums.VoiceTagType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateVoiceTagRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull VoiceTagType tagType,
        @Size(max = 2000) String sourceText,
        @Size(max = 20) String languageCode,
        @Size(max = 512) String s3Key,
        @Positive Integer durationSeconds,
        @Positive Long fileSizeBytes
) {
}
