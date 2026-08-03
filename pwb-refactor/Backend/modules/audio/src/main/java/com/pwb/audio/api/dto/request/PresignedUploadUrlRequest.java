package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PresignedUploadUrlRequest(
        @NotBlank @Size(max = 20) String format
) {
}
