package com.pwb.backend.modules.audio.entity;

import com.pwb.backend.common.model.BaseEntity;
import com.pwb.backend.modules.audio.enums.DemoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Demo extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "original_s3_key", nullable = false, length = 255, updatable = false)
    private String originalS3Key;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "hls_playlist_s3_key", length = 255)
    private String hlsPlaylistS3Key;

    @Column(name = "voice_tag_id")
    private UUID voiceTagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DemoStatus status;

    @Column(name = "duration", precision = 10, scale = 2)
    private BigDecimal duration;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "format", length = 10)
    private String format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waveform_data", columnDefinition = "jsonb")
    private String waveformData;

    @Column(name = "aes_key_encrypted")
    private byte[] aesKeyEncrypted;

    @Column(name = "aes_key_version", nullable = false)
    private int aesKeyVersion = 1;

    @Column(name = "previous_aes_key_encrypted")
    private byte[] previousAesKeyEncrypted;

    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public Demo(UUID id, UUID ownerId, String title, String originalS3Key, long fileSize, UUID voiceTagId) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.originalS3Key = originalS3Key;
        this.fileSize = fileSize;
        this.voiceTagId = voiceTagId;
        this.status = DemoStatus.PROCESSING;
        this.aesKeyVersion = 1;
    }

    public void rotateKey(byte[] newEncryptedKey, Instant rotatedAt) {
        if (this.aesKeyEncrypted != null && this.aesKeyEncrypted.length > 0) {
            this.previousAesKeyEncrypted = this.aesKeyEncrypted;
        }
        this.aesKeyEncrypted = newEncryptedKey;
        this.aesKeyVersion = this.aesKeyVersion + 1;
        this.lastRotatedAt = rotatedAt;
    }

    public void clearPreviousKey() {
        this.previousAesKeyEncrypted = null;
    }
}