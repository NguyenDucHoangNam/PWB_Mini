package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.enums.SongStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Storage keys are deliberately absent: clients reach the audio through the presigned-url endpoints,
 * so exposing the bucket layout would leak internals for no benefit.
 */
public record SongResponse(
        UUID id,
        UUID userId,
        String title,
        String artist,
        String album,
        Long fileSizeBytes,
        Integer durationSeconds,
        String format,
        SongStatus status,
        String thumbnailUrl,
        String lastError,
        boolean processed,
        Instant createdAt,
        Instant updatedAt
) {

    public static SongResponse from(SongView view) {
        return new SongResponse(
                view.id(),
                view.userId(),
                view.title(),
                view.artist(),
                view.album(),
                view.fileSizeBytes(),
                view.durationSeconds(),
                view.format(),
                view.status(),
                view.thumbnailUrl(),
                view.lastError(),
                view.processed(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
