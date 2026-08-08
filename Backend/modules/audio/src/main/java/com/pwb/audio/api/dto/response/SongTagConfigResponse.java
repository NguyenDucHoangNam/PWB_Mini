package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.SongTagConfigView;

import java.time.Instant;
import java.util.UUID;

public record SongTagConfigResponse(
        UUID id,
        UUID songId,
        UUID voiceTagId,
        String voiceTagName,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer duckingPercentage,
        Integer startOffsetSeconds,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static SongTagConfigResponse from(SongTagConfigView view) {
        return new SongTagConfigResponse(
                view.id(),
                view.songId(),
                view.voiceTagId(),
                view.voiceTagName(),
                view.intervalSeconds(),
                view.volumePercentage(),
                view.duckingPercentage(),
                view.startOffsetSeconds(),
                view.enabled(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
