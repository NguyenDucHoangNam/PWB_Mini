package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadSongRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String artist,
        @Size(max = 200) String album,
        @NotBlank @Size(max = 512) String originalS3Key,
        @NotNull @Positive Long fileSizeBytes,
        @NotNull @Positive Integer durationSeconds,
        @NotBlank @Size(max = 20) String format
) {
}
