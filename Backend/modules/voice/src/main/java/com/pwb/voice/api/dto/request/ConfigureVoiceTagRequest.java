package com.pwb.voice.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigureVoiceTagRequest {

    @NotNull(message = "{validation.voicetag.required}")
    private UUID voiceTagId;

    @NotNull(message = "{validation.interval.required}")
    @Min(value = 1, message = "{validation.interval.min}")
    private Integer intervalSeconds;

    @NotNull(message = "{validation.volume.required}")
    @Min(value = 0, message = "{validation.volume.range}")
    @Max(value = 100, message = "{validation.volume.range}")
    private Integer volumePercentage;

    @NotNull(message = "{validation.fadein.required}")
    @Min(value = 0, message = "{validation.fade.min}")
    private Integer fadeInDurationMs;

    @NotNull(message = "{validation.fadeout.required}")
    @Min(value = 0, message = "{validation.fade.min}")
    private Integer fadeOutDurationMs;

    @Min(value = 0, message = "{validation.startoffset.min}")
    private Integer startOffsetSeconds;

    private Boolean enabled;
}
