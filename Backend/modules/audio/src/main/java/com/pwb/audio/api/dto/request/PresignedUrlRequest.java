package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PresignedUrlRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotNull @Min(60) @Max(86400) Long expirationSeconds
) {
}
