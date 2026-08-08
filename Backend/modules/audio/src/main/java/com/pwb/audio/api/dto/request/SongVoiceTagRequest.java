package com.pwb.audio.api.dto.request;

import com.pwb.audio.application.command.VoiceTagSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The voice tag a song is uploaded with, supplied only as part of {@link CreateSongRequest}. There is no
 * endpoint that edits it afterwards: the settings are merged into the audio once and the result is final.
 *
 * @param intervalSeconds     gap between the start of one tag and the start of the next
 * @param startOffsetSeconds  when the first tag is placed
 * @param volumePercentage    how loud the voice tag itself plays
 * @param duckingPercentage   volume the song keeps while a tag plays; 100 leaves the song untouched
 */
public record SongVoiceTagRequest(
        @NotNull UUID voiceTagId,
        @NotNull @Min(5) @Max(600) Integer intervalSeconds,
        @NotNull @Min(0) @Max(100) Integer volumePercentage,
        @NotNull @Min(0) @Max(100) Integer duckingPercentage,
        @NotNull @Min(0) Integer startOffsetSeconds,
        boolean enabled
) {

    public VoiceTagSettings toSettings() {
        return new VoiceTagSettings(
                voiceTagId,
                intervalSeconds,
                volumePercentage,
                duckingPercentage,
                startOffsetSeconds,
                enabled
        );
    }
}
