package com.pwb.backend.modules.audio.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConfirmUploadRequest(

        @NotBlank(message = "{validation.audio.s3Key.required}")
        @Size(max = 255, message = "{validation.audio.s3Key.length}")
        String s3Key,

        @NotBlank(message = "{validation.audio.title.required}")
        @Size(min = 2, max = 100, message = "{validation.audio.title.length}")
        String title,

        UUID voiceTagId,

        @Min(value = 10, message = "{validation.audio.watermarkInterval.min}")
        Integer watermarkInterval) {
}