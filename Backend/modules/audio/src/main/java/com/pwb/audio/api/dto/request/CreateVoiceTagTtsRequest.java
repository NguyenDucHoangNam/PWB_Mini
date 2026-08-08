package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVoiceTagTtsRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String text,
        // @Pattern alone would wave a null through — Bean Validation skips null by design — and the
        // synthesiser would then quietly fall back to its English default.
        @NotBlank(message = "validation.languagecode.required")
        @Pattern(regexp = "^(vi-VN|en-US|en-GB)$", message = "validation.languagecode.pattern")
        @Size(max = 8) String languageCode,
        /** Null means "use the provider default for this language". */
        @Size(max = 64) String voiceName
) {
}
