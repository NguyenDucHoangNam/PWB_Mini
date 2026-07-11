package com.pwb.backend.audio.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmUploadRequest(
    @NotBlank
    @Pattern(regexp = "^original/[A-Za-z0-9_-]{1,128}/[A-Za-z0-9-]{36}\\.(wav|flac|mp3)$",
        message = "s3Key has invalid format")
    String s3Key,

    @NotBlank
    @Size(max = 100, message = "title must not exceed 100 characters")
    @Pattern(regexp = "^[\\P{Cc}]{1,100}$",
        message = "title must not contain control characters")
    String title,

    String voiceTagId,

    @Min(value = 10, message = "watermarkInterval must be at least 10 seconds")
    @Max(value = 60, message = "watermarkInterval must not exceed 60 seconds")
    Integer watermarkInterval
) {}
