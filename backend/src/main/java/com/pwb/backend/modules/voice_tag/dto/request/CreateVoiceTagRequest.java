package com.pwb.backend.modules.voice_tag.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVoiceTagRequest(

        @NotBlank(message = "{validation.voiceTag.textContent.required}")
        @Size(max = 250, message = "{validation.voiceTag.textContent.length}")
        String textContent,

        @NotBlank(message = "{validation.voiceTag.languageCode.required}")
        @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "{validation.voiceTag.languageCode.format}")
        String languageCode,

        @NotBlank(message = "{validation.voiceTag.voiceName.required}")
        @Pattern(
                regexp = "^[a-z]{2,3}-[A-Z]{2,3}-.+$",
                message = "{validation.voiceTag.voiceName.format}")
        String voiceName) {
}
