package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateVoiceTagRequest(
        @Size(max = 100) String name,
        @Size(max = 2000) String sourceText,
        @Size(max = 20) String languageCode
) {
}
