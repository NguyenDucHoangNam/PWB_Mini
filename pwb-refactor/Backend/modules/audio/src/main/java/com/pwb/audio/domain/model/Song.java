package com.pwb.audio.domain.model;

import com.pwb.audio.domain.enums.SongStatus;
import com.pwb.audio.domain.model.vo.AudioFormat;
import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class Song extends DomainBaseEntity {

    private final UUID id;
    private final UUID userId;
    private String title;
    private String artist;
    private String album;
    private String originalS3Key;
    private String processedS3Key;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private AudioFormat format;
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
            AudioFormat format,
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
            AudioFormat format
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (originalS3Key == null || originalS3Key.isBlank()) {
            throw new IllegalArgumentException("originalS3Key must not be blank");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
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
        AudioFormat audioFormat = (format == null) ? null : AudioFormat.of(format);
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
                audioFormat,
                status,
                thumbnailUrl,
                lastError
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getOriginalS3Key() {
        return originalS3Key;
    }

    public String getProcessedS3Key() {
        return processedS3Key;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public AudioFormat getFormat() {
        return format;
    }

    public SongStatus getStatus() {
        return status;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getLastError() {
        return lastError;
    }

    public void updateMetadata(String title, String artist, String album) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.artist = artist;
        this.album = album;
        touch();
    }

    public void markProcessing() {
        if (this.status == SongStatus.PROCESSING) {
            throw new IllegalStateException("Song is already processing");
        }
        this.processedS3Key = null;
        this.status = SongStatus.PROCESSING;
        this.lastError = null;
        touch();
    }

    public void markProcessed(String processedS3Key, Integer durationSeconds) {
        if (this.status != SongStatus.PROCESSING) {
            throw new IllegalStateException("Song must be in PROCESSING status to mark as processed");
        }
        this.processedS3Key = processedS3Key;
        this.durationSeconds = durationSeconds;
        this.status = SongStatus.PROCESSED;
        touch();
    }

    public void markFailed(String error) {
        this.status = SongStatus.FAILED;
        this.lastError = error;
        touch();
    }

    public void clearProcessedKey() {
        this.processedS3Key = null;
        touch();
    }

    public boolean isProcessed() {
        return this.status == SongStatus.PROCESSED && this.processedS3Key != null;
    }

    public boolean isFailed() {
        return this.status == SongStatus.FAILED;
    }

    public boolean canTriggerProcessing() {
        return this.status == SongStatus.UPLOADED || this.status == SongStatus.FAILED;
    }

    public void triggerProcessing() {
        if (!canTriggerProcessing()) {
            throw new ProcessingStateException("Cannot trigger processing from current status: " + status);
        }
        this.processedS3Key = null;
        this.status = SongStatus.PROCESSING;
        this.lastError = null;
        touch();
    }

    public void markDeleted() {
        this.status = SongStatus.DELETED;
        this.processedS3Key = null;
        touch();
    }

    public static class ProcessingStateException extends RuntimeException {
        public ProcessingStateException(String message) {
            super(message);
        }
    }
}
