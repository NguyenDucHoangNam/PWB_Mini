package com.pwb.audio.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadSongMetadata(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 255) String originalFilename
) {
}