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
        String lastError,
        boolean processed,
        /** Whether a voice tag was merged into this song. The tag's identity is not part of a listing. */
        boolean hasVoiceTag,
        Instant createdAt,
        Instant updatedAt
) {
}
