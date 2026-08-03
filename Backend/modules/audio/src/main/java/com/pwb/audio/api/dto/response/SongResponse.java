package com.pwb.audio.api.dto.response;

import com.pwb.audio.application.view.SongView;
import com.pwb.audio.domain.enums.SongStatus;

import java.time.Instant;
import java.util.UUID;

public record SongResponse(
        UUID id,
        UUID userId,
        String title,
        String artist,
        String album,
        String originalS3Key,
        String processedS3Key,
        Long fileSizeBytes,
        Integer durationSeconds,
        String format,
        SongStatus status,
        String thumbnailUrl,
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
                view.originalS3Key(),
                view.processedS3Key(),
                view.fileSizeBytes(),
                view.durationSeconds(),
                view.format(),
                view.status(),
                view.thumbnailUrl(),
                view.processed(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
