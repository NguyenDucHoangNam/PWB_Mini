package com.pwb.voice.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSongRequest {

    @NotBlank(message = "{validation.title.required}")
    @Size(max = 256, message = "{validation.title.maxlength}")
    private String title;

    @Size(max = 256, message = "{validation.artist.maxlength}")
    private String artist;

    @Size(max = 256, message = "{validation.album.maxlength}")
    private String album;

    @Valid
    private ConfigureVoiceTagRequest voiceTagConfig;
}
