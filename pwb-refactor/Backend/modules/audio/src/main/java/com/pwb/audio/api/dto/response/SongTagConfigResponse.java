package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.SongTagConfigView;

import java.time.Instant;
import java.util.UUID;

public record SongTagConfigResponse(
        UUID id,
        UUID songId,
        UUID voiceTagId,
        Integer intervalSeconds,
        Integer volumePercentage,
        Integer fadeInDurationMs,
        Integer fadeOutDurationMs,
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
                view.intervalSeconds(),
                view.volumePercentage(),
                view.fadeInDurationMs(),
                view.fadeOutDurationMs(),
                view.startOffsetSeconds(),
                view.enabled(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
