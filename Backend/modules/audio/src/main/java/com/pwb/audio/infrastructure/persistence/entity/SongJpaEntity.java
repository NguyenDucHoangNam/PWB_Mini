package com.pwb.audio.infrastructure.persistence.entity;

import com.pwb.audio.domain.enums.SongStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "audio_songs",
        indexes = {
                @Index(name = "ix_audio_songs_user_id", columnList = "user_id"),
                @Index(name = "ix_audio_songs_status", columnList = "status"),
                @Index(name = "ix_audio_songs_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongJpaEntity extends AudioJpaBaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "artist", length = 256)
    private String artist;

    @Column(name = "album", length = 256)
    private String album;

    @Column(name = "original_s3_key", nullable = false, length = 512)
    private String originalS3Key;

    @Column(name = "processed_s3_key", length = 512)
    private String processedS3Key;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "format", length = 16)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SongStatus status;

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Column(name = "last_error", length = 1024)
    private String lastError;
}
