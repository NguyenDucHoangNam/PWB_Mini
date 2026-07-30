package com.pwb.audio.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateSongRequest(
        @Size(max = 200) String title,
        @Size(max = 200) String artist,
        @Size(max = 200) String album
) {
}
