package com.pwb.backend.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVoiceTagRequest(
    @NotBlank
    @Size(max = 250, message = "SSML input must not exceed 250 characters")
    String textContent,

    @NotBlank
    @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "Language code must follow BCP-47 format (e.g. vi-VN)")
    String languageCode,

    @NotBlank
    @Pattern(
        regexp = "^[a-z]{2,3}-[A-Z]{2,3}-(Standard|Neural|Wavenet)-[A-Z]{1,3}$",
        message = "Voice name must follow GCP voice naming format"
    )
    String voiceName
) {}
