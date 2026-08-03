package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVoiceTagTtsRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String text,
        @Pattern(regexp = "^(vi-VN|en-US|en-GB)$", message = "validation.languagecode.pattern")
        @Size(max = 8) String languageCode
) {
}
