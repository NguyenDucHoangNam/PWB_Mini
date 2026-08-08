package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UploadUrlRequest(
        @NotBlank @Size(max = 16) String format
) {
}
