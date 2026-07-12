package com.pwb.backend.modules.audio.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PresignedUrlRequest(

        @NotBlank(message = "{validation.audio.filename.required}")
        @Size(max = 255, message = "{validation.audio.filename.length}")
        String fileName,

        @NotBlank(message = "{validation.audio.contentType.required}")
        @Pattern(
                regexp = "audio/(wav|wave|flac|x-flac|mp3|mpeg)",
                message = "{validation.audio.contentType.format}")
        String contentType,

        @NotNull(message = "{validation.audio.fileSize.required}")
        @Min(value = 1, message = "{validation.audio.fileSize.min}")
        long fileSize) {
}