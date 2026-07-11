package com.pwb.backend.audio.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9._\\- ]{1,255}$",
        message = "fileName contains invalid characters")
    String fileName,

    @NotNull
    @Min(value = 1, message = "fileSize must be at least 1 byte")
    @Max(value = 209715200, message = "fileSize must not exceed 200MB")
    Long fileSize,

    @NotBlank
    @Pattern(regexp = "^audio/(wav|x-wav|wave|flac|x-flac|mpeg|mp3)$",
        message = "contentType must be one of audio/wav, audio/flac, audio/mpeg")
    String contentType
) {}
