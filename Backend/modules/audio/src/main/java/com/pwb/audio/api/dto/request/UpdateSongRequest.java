package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSongRequest(
        @NotBlank @Size(max = 200) String title
) {
}
