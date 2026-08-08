package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PreviewVoiceTagTtsRequest(
        @NotBlank @Size(max = 2000) String text,
        @NotBlank(message = "validation.languagecode.required")
        @Pattern(regexp = "^(vi-VN|en-US|en-GB)$", message = "validation.languagecode.pattern")
        @Size(max = 8) String languageCode,
        @Size(max = 64) String voiceName
) {
}
