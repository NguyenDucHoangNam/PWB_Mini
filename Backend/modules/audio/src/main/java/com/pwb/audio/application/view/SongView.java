package com.pwb.audio.application.view;

import com.pwb.audio.domain.enums.SongStatus;

import java.time.Instant;
import java.util.UUID;

public record SongView(
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
    public static SongView from(
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
            Instant createdAt,
            Instant updatedAt
    ) {
        boolean isProcessed = processedS3Key != null && !processedS3Key.isBlank();
        return new SongView(
                id, userId, title, artist, album,
                originalS3Key, processedS3Key, fileSizeBytes,
                durationSeconds, format, status, thumbnailUrl,
                isProcessed, createdAt, updatedAt
        );
    }
}
