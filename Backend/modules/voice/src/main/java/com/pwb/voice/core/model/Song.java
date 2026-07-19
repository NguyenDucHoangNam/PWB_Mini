package com.pwb.voice.core.model;

import com.pwb.voice.api.enums.SongStatus;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class Song {

    private final UUID id;
    private final UUID userId;
    private String title;
    private String artist;
    private String album;
    private String originalS3Key;
    private String processedS3Key;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private String format;
    private SongStatus status;
    private String thumbnailUrl;
    private String lastError;

    private Song(
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
            String lastError
    ) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.originalS3Key = originalS3Key;
        this.processedS3Key = processedS3Key;
        this.fileSizeBytes = fileSizeBytes;
        this.durationSeconds = durationSeconds;
        this.format = format;
        this.status = status;
        this.thumbnailUrl = thumbnailUrl;
        this.lastError = lastError;
    }

    public static Song create(
            UUID userId,
            String title,
            String artist,
            String album,
            String originalS3Key,
            Long fileSizeBytes,
            Integer durationSeconds,
            String format
    ) {
        return new Song(
                UUID.randomUUID(),
                userId,
                title,
                artist,
                album,
                originalS3Key,
                null,
                fileSizeBytes,
                durationSeconds,
                format,
                SongStatus.UPLOADED,
                null,
                null
        );
    }

    public static Song rehydrate(
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
            String lastError
    ) {
        return new Song(
                id,
                userId,
                title,
                artist,
                album,
                originalS3Key,
                processedS3Key,
                fileSizeBytes,
                durationSeconds,
                format,
                status,
                thumbnailUrl,
                lastError
        );
    }

    public void updateMetadata(String title, String artist, String album) {
        if (title != null) this.title = title;
        if (artist != null) this.artist = artist;
        if (album != null) this.album = album;
    }

    public void markProcessing() {
        if (status == SongStatus.PROCESSING) {
            throw new IllegalStateException("Song is already processing");
        }
        this.processedS3Key = null;
        this.lastError = null;
        this.status = SongStatus.PROCESSING;
    }

    public void markProcessed(String processedS3Key, Integer durationSeconds) {
        this.processedS3Key = processedS3Key;
        this.durationSeconds = durationSeconds;
        this.status = SongStatus.PROCESSED;
        this.lastError = null;
    }

    public void markFailed(String lastError) {
        this.status = SongStatus.FAILED;
        this.lastError = lastError;
    }

    public void clearProcessedKey() {
        this.processedS3Key = null;
    }
}
